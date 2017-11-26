package io.chiv.livelondonbuses.map;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.ui.IconGenerator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.chiv.livelondonbuses.R;
import io.chiv.livelondonbuses.models.BusMarker;
import io.chiv.livelondonbuses.models.PositionData;
import io.chiv.livelondonbuses.utils.MarkerAnimation;
import static io.chiv.livelondonbuses.utils.MapUtils.*;
import static io.chiv.livelondonbuses.utils.JsonHelpers.*;


public class Map extends FragmentActivity implements OnMapReadyCallback, MapCallbacks {

    private static final String TAG = "MapActivity";
    private GoogleMap mMap;
    private ServerClient serverClient;
    private String uuid;

    private List<String> selectedRoutes = Collections.synchronizedList(new ArrayList<>());
    private String idCurrentlySelected;
    private String stopIdForNextStopInfoBoxCurrentlyOpen;

    private ConcurrentHashMap<String, BusMarker> markerMap = new ConcurrentHashMap<>();
    private ArrayList<Marker> nextStopsMarkersActive = new ArrayList<>();
    private ArrayList<Polyline> nextStopsPolylinesActive = new ArrayList<>();

    private Handler actionHandler = new Handler();
    private Timer timer = new Timer();
    private IconGenerator iconGenerator;

    private AtomicInteger BUS_MARKER_Z_INDEX_NEXT_AVAILABLE = new AtomicInteger(100);
    private LatLng STARTING_LAT_LNG = new LatLng(51.505485, -0.127889);


    @Override
    protected void onPause() {
        Log.i(TAG, "Activity Paused");
        if (serverClient != null) {
            serverClient.closeDataStream("Application paused (probably in background)");
        }
        clearMap();
        idCurrentlySelected = null;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Activity resumed");
        generateNewUUID();
        if (serverClient != null) {
            String filteringParams = getFilteringParams();
            serverClient.openDataStream(uuid, this, filteringParams);
            serverClient.updateParamsAndGetSnapshot(uuid, filteringParams, this);
        }
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }
        setContentView(R.layout.activity_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setPadding(0, 120, 0, 0);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setRotateGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableMyLocationLayer();
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(STARTING_LAT_LNG, 11));

        serverClient = ServerClient.newInstance(getApplicationContext(), this);
        serverClient.updateRouteList(this);
        serverClient.openDataStream(uuid, this, null);

        setUpIconFactory();
        setCleanUpTimer();
        setInfoWindowAdapter();
        setOnCameraIdleListener();
        setOnMarkerClickListener();
        setOnMarkerCloseListener();

    }

    private void setOnCameraIdleListener() {
        mMap.setOnCameraIdleListener(() -> {
            Log.i(TAG, "On camera idle firing");
            onBoundsChange(getWidenedBounds(getBounds()));
        });
    }

    private void setOnMarkerCloseListener() {
        mMap.setOnInfoWindowCloseListener(marker -> stopIdForNextStopInfoBoxCurrentlyOpen = null);
    }

    private void setOnMarkerClickListener() {
        mMap.setOnMarkerClickListener(marker -> {

            if (idCurrentlySelected != null) {
                markerMap.get(idCurrentlySelected).changeIconTextStyle(iconGenerator, R.style.standardRouteMarkerText);
            }
            String tag = (String) marker.getTag();
            if (tag != null && tag.startsWith("BUS")) {
                String id = tag.substring(7);
                idCurrentlySelected = id;
                changeMarkerStyleToActive(id);
                try {
                    JSONObject idJson = new JSONObject(id);
                    serverClient.getNextStopsMarkerAndPolylineData(
                            id,
                            idJson.getString("vehicleId"),
                            idJson.getString("routeId"),
                            idJson.getString("direction"),
                            this, null);
                } catch (JSONException e) {
                    Log.e(TAG, "Unable to decode json ID for marker. Json retrieved: [" + id + "]", e);
                }
            } else if (tag != null && tag.startsWith("NXTSTP")) {
                try {
                    JSONObject tagJson = new JSONObject(tag.substring(7));
                    stopIdForNextStopInfoBoxCurrentlyOpen = tagJson.getString("stopId");
                    idCurrentlySelected = tagJson.getJSONObject("id").toString();
                    markerMap.get(idCurrentlySelected).changeIconTextStyle(iconGenerator, R.style.activeRouteMarkerText);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
    }

    private void enableMyLocationLayer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            }
        } else {
            mMap.setMyLocationEnabled(true);
        }
    }

    private void setCleanUpTimer() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clearUpExpired();
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000);
    }

    private void setUpIconFactory() {
        iconGenerator = new IconGenerator(this);
    }

    private void addRouteSelectionButton(final ArrayList<String> routeList) {
        Button button = new Button(this);
        button.setText(R.string.selectRoutes);
        addContentView(button, new ActionBar.LayoutParams(ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        button.setOnClickListener(v -> showRouteSelector(routeList));
    }

    private void showRouteSelector(ArrayList<String> routeList) {

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        RouteSelector rs = RouteSelector.newInstance(routeList, selectedRoutes);
        rs.show(ft, "dialog");

    }

    @Override
    public void onRouteListUpdated(ArrayList<String> routeList) {
        addRouteSelectionButton(routeList);
    }

    @Override
    public void onRouteSelectionReturn(ArrayList<String> newlySelectedRoutes) {
        //TODO delete existing routes etc

        List<String> routesToDelete = selectedRoutes;
        routesToDelete.removeAll(newlySelectedRoutes);
        clearRoutes(routesToDelete);

        selectedRoutes = newlySelectedRoutes;
        serverClient.updateParamsAndGetSnapshot(uuid, getFilteringParams(), this);
    }

    @Override
    public void onSnapshotReceived(JSONArray jsonArray) {

        JSONArray filteredJsonArray = new JSONArray();
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObj = jsonArray.getJSONObject(i);
                String routeId = jsonObj.getJSONObject("busRoute").getString("id");
                String direction = jsonObj.getJSONObject("busRoute").getString("direction");
                String vehicleId = jsonObj.getString("vehicleId");
                String id = getId(vehicleId, routeId, direction);
                if (!markerMap.containsKey(id)) {
                    filteredJsonArray.put(jsonObj);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Unable to decode snapshot data. Data received [" + jsonArray + "]", e);
            }
        }
        handlePositionJson(filteredJsonArray);
    }

    @Override
    public void onPositionJsonReceived(JSONArray jsonArray) {
        handlePositionJson(jsonArray);
    }

    @Override
    public void onNextStopMarkerAndPolylineJsonReceived(String id, JSONArray jsonArray, String autoOpenForNextStopId) {
        deleteActiveNextStopMarkers();
        deleteActiveNextStopPolylines();

        try {
            BusMarker marker = markerMap.get(id);
            idCurrentlySelected = id;
            marker.changeIconTextStyle(iconGenerator, R.style.activeRouteMarkerText);

            //Set first stop polyline
            try {
                LatLng markerCurrentPosition = marker.getImageMarker().getPosition();
                LatLng firstStopPosition = latLngFrom(jsonArray.getJSONObject(0).getJSONObject("busStop").getJSONObject("latLng"));
                createNextStopPolyline(new ArrayList<>(Arrays.asList(markerCurrentPosition, firstStopPosition)));
            } catch (JSONException e) {
                Log.e(TAG, "Unable to decode first stop position for nextStop data received. Json received [" + jsonArray + "]", e);
            }
        } catch (NullPointerException e) {
            Log.e(TAG, "Unable to get marker for id [" + id + "]", e);
        }

        //Set other markers and polylines
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject nextStopObj = jsonArray.getJSONObject(i);
                String stopId = nextStopObj.getJSONObject("busStop").getString("stopID");
                String stopName = nextStopObj.getJSONObject("busStop").getString("stopName");
                Long predictedArrival = nextStopObj.getLong("predictedArrival");
                LatLng latLng = latLngFrom(nextStopObj.getJSONObject("busStop").getJSONObject("latLng"));
                ArrayList<LatLng> polylineArr = latLngListFrom(nextStopObj.optJSONArray("polylineToNext"));

                boolean autoOpen = (autoOpenForNextStopId != null && autoOpenForNextStopId.equals(stopId));
                createNextStopMarker(id, latLng, stopName, stopId, predictedArrival, autoOpen);
                createNextStopPolyline(polylineArr);

            } catch (JSONException e) {
                Log.e(TAG, "Error decoding next stops json. Json string received: [" + jsonArray.toString() + "]", e);
            }
        }
    }

    void handlePositionJson(JSONArray jsonArray) {
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                PositionData posData = new PositionData(jsonArray.getJSONObject(i));

                if (isReceivedRouteInSelected(posData.getRouteId()) && isReceivedIndexAheadOfLastHandled(posData.getNextStopIndex(), posData.getId())) {

                    deleteExistingMarkerIfClientRunningBehind(posData.getId(), posData.isDeleteAfter(), posData.getNextStopIndex());

                    if (!markerMap.containsKey(posData.getId())) {
                        markerMap.put(posData.getId(),
                                createBusMarker(
                                        posData.getId(),
                                        posData.getRouteId(),
                                        posData.getStartingLatLng(),
                                        posData.getNextStopName(),
                                        posData.getVehicleId(),
                                        posData.getDestination()));
                    }

                    BusMarker busMarker = markerMap.get(posData.getId());

                    Long now = System.currentTimeMillis();
                    Long timeNextFree = getOrElse(busMarker.getTimeNextFree(), 0L);
                    Long adjustedStartingTime = posData.getStartingTime() < timeNextFree ? timeNextFree : posData.getStartingTime();
                    Long waitTimeBeforeStart = adjustedStartingTime - now;
                    Long timeAccumulator = waitTimeBeforeStart < 0 ? 0 : waitTimeBeforeStart;
                    Long realTimeToNextStop = posData.getNextStopArrivalTime() - (adjustedStartingTime < now ? now : adjustedStartingTime);
                    Long timeToNextStop = realTimeToNextStop > 0 ? realTimeToNextStop : 5000;

                    createOrUpdateBusInfoBox(
                            busMarker,
                            posData.getRouteId(),
                            posData.getNextStopName(),
                            posData.getVehicleId(),
                            posData.getDestination(),
                            timeAccumulator);

                    if (idCurrentlySelected != null && idCurrentlySelected.equals(posData.getId())) {
                        String autoOpenForStopId = stopIdForNextStopInfoBoxCurrentlyOpen;
                        serverClient.getNextStopsMarkerAndPolylineData(
                                posData.getId(),
                                posData.getVehicleId(),
                                posData.getRouteId(),
                                posData.getDirection(), this, autoOpenForStopId);
                    }

                    for (int j = 0; j < posData.getMovementInstructions().length(); j++) {
                        JSONObject movementJsonObj = posData.getMovementInstructions().getJSONObject(j);
                        LatLng latLngToMoveTo = latLngFrom(movementJsonObj.getJSONObject("to"));
                        Integer angle = movementJsonObj.getInt("angle");
                        Double proportionalDistance = movementJsonObj.getDouble("proportion");
                        Integer proportionalTime = Double.valueOf(timeToNextStop * proportionalDistance).intValue();

                        animateMarker(busMarker, proportionalTime, latLngToMoveTo, angle, timeAccumulator);
                        timeAccumulator += proportionalTime;
                    }

                    if (posData.isDeleteAfter()) {
                        deleteBusMarker(posData.getId(), timeAccumulator);
                    }

                    busMarker.setTimeNextFree(posData.getNextStopArrivalTime());
                    busMarker.setLastHandledNextIndex(posData.getNextStopIndex());
                }
            } catch (JSONException e) {
                Log.e(TAG, "Unable to decode json position data received. Json data: [" + jsonArray + "]", e);
            }
        }
    }



    private boolean isReceivedIndexAheadOfLastHandled(Integer receivedIndex, String id) {
        BusMarker busMarker = markerMap.get(id);
        if (busMarker != null) {
            Integer lastHandledNextIndex = busMarker.getLastHandledNextIndex();
            return lastHandledNextIndex == null || (receivedIndex > lastHandledNextIndex);
        } else {
            return true;
        }
    }

    private boolean isReceivedRouteInSelected(String routeId) {
        return selectedRoutes.contains(routeId);
    }

    private void createNextStopMarker(String id, LatLng latLng, String stopName, String stopId, Long predictedArrival, Boolean openInfoBox) {

        int NEXT_STOP_MARKER_Z_INDEX = 50;
        MarkerOptions nextStopMarkerOptions = new MarkerOptions()
                .position(new LatLng(latLng.latitude, latLng.longitude))
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.mm_20_orange))
                .title(stopName + " (" + stopId + ")")
                .snippet("Predicted arrival time: " + DateFormat.getTimeInstance().format(predictedArrival))
                .zIndex(NEXT_STOP_MARKER_Z_INDEX)
                .visible(true);

        Marker marker = mMap.addMarker(nextStopMarkerOptions);
        marker.setTag("NXTSTP-" + "{\"id\":" + id + ", \"stopId\":\"" + stopId + "\"}");
        if (openInfoBox) {
            marker.showInfoWindow();
            stopIdForNextStopInfoBoxCurrentlyOpen = stopId;
        }
        nextStopsMarkersActive.add(marker);
    }

    private void createNextStopPolyline(ArrayList<LatLng> polyLineToNext) {
        if (polyLineToNext != null) {
            PolylineOptions polylineOpts = new PolylineOptions()
                    .addAll(polyLineToNext)
                    .color(Color.RED);

            Polyline polyline = mMap.addPolyline(polylineOpts);
            nextStopsPolylinesActive.add(polyline);
        }
    }

    private BusMarker createBusMarker(String id, String routeId, LatLng latLng, String nextStopName, String vehicleId, String destination) {

        MarkerOptions imageMarkerOptions = new MarkerOptions()
                .position(new LatLng(latLng.latitude + 0.000005, latLng.longitude + 0.000005)) // Offset necessary to avoid flickering markers in UI
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.busicon))
                .title("Route " + routeId + " (" + vehicleId + ")")
                .snippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName)
                .anchor(0.5f, 0.5f)
                .zIndex(BUS_MARKER_Z_INDEX_NEXT_AVAILABLE.getAndIncrement())
                .visible(false);

        MarkerOptions textMarkerOptions = new MarkerOptions()
                .icon(makeIconForRoute(iconGenerator, routeId, R.style.standardRouteMarkerText))
                .position(latLng)
                .title("Route " + routeId + " (" + vehicleId + ")")
                .snippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName)
                .anchor(0.5f, 0.5f)
                .zIndex(BUS_MARKER_Z_INDEX_NEXT_AVAILABLE.getAndIncrement())
                .visible(false);

        Marker imageMarker = mMap.addMarker(imageMarkerOptions);
        imageMarker.setTag("BUSIMG-" + id);
        imageMarker.setInfoWindowAnchor(0.5f, 0.5f);
        Marker textMarker = mMap.addMarker(textMarkerOptions);
        textMarker.setTag("BUSTXT-" + id);
        textMarker.setInfoWindowAnchor(0.5f, 0.5f);

        return new BusMarker(id, routeId, imageMarker, textMarker);
    }

    private void changeMarkerStyleToActive(String id) {
        BusMarker marker = markerMap.get(id);
        if (marker != null) {
            marker.changeIconTextStyle(iconGenerator, R.style.activeRouteMarkerText);
        }
    }

    private void deleteBusMarker(String id, Long waitTime) {
        actionHandler.postDelayed(() -> {
            BusMarker marker = markerMap.get(id);
            if (marker != null) {
                marker.delete(actionHandler);
            }
            markerMap.remove(id);
        }, waitTime);
    }

    private void deleteActiveNextStopMarkers() {
        for (int i = 0; i < nextStopsMarkersActive.size(); i++) {
            nextStopsMarkersActive.get(i).remove();
        }
        nextStopsMarkersActive.clear();
    }

    private void deleteActiveNextStopPolylines() {
        for (int i = 0; i < nextStopsPolylinesActive.size(); i++) {
            nextStopsPolylinesActive.get(i).remove();
        }
        nextStopsPolylinesActive.clear();
    }

    private void deleteExistingMarkerIfClientRunningBehind(String id, Boolean deleteAfter, Integer nextStopIndex) {
        BusMarker busMarker = markerMap.get(id);
        if (busMarker != null && busMarker.getLastHandledNextIndex() != null) {
            if (!deleteAfter && nextStopIndex > busMarker.getLastHandledNextIndex() + 1) {
                deleteBusMarker(id, 0L);
            }
        }
    }

    private void animateMarker(BusMarker busMarker, Integer duration, LatLng moveTo, Integer rotation, Long waitTime) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                busMarker.getImageMarker().setVisible(true);
                busMarker.getTextMarker().setVisible(true);
                busMarker.getImageMarker().setRotation(rotation);
                MarkerAnimation.animateMarkerToHC(busMarker, moveTo, duration);

                if (busMarker.getAnimationInstructions() != null) {
                    busMarker.getAnimationInstructions().remove(this);
                }
            }
        };

        busMarker.getAnimationInstructions().add(runnable);
        actionHandler.postDelayed(runnable, waitTime);
    }


    private LatLngBounds getBounds() {
        return mMap.getProjection().getVisibleRegion().latLngBounds;
    }

    private void generateNewUUID() {
        uuid = UUID.randomUUID().toString();
    }



    private String getFilteringParams() {
        JSONArray routesArray = getSelectedRoutesJsonArray(selectedRoutes);
        LatLngBounds expandedBounds = getWidenedBounds(getBounds());
        JSONObject latLngBoundsObj = getLatLngBoundsJsonObj(expandedBounds);
        return getFilteringParamsJsonStr(latLngBoundsObj, routesArray);
    }


    private void clearRoutes(List<String> routesToClear) {
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            for (int i = 0; i < routesToClear.size(); i++) {
                try {
                    JSONObject id = new JSONObject(entry.getKey());
                    if (id.getString("routeId").equals(routesToClear.get(i))) {
                        deleteBusMarker(entry.getKey(), 0L);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error: Json decoding error on ID: [" + entry.getKey() + "]");
                }
            }
        }
    }

    private void clearMap() {
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            deleteBusMarker(entry.getKey(), 0L);
            deleteActiveNextStopMarkers();
            deleteActiveNextStopPolylines();
        }
    }

    private void clearUpExpired() {
        Long now = System.currentTimeMillis();
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            if (now - entry.getValue().getTimeNextFree() > 600000) {
                deleteBusMarker(entry.getKey(), 0L);
            }
        }
    }

    private void onBoundsChange(LatLngBounds bounds) {
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            if (!(bounds.contains(entry.getValue().getImageMarker().getPosition()))) {
                if (!entry.getValue().getId().equals(idCurrentlySelected)) {
                    deleteBusMarker(entry.getKey(), 0L);
                }
            }
        }
        serverClient.updateParamsAndGetSnapshot(uuid, getFilteringParams(), this);
    }

    private void createOrUpdateBusInfoBox(BusMarker busMarker, String routeId, String nextStopName, String vehicleId, String destination, Long waitTime) {
        Runnable runnable = () -> {
            Marker imageMarker = busMarker.getImageMarker();
            Marker textMarker = busMarker.getTextMarker();
            imageMarker.setTitle("Route " + routeId + " (" + vehicleId + ")");
            textMarker.setTitle("Route " + routeId + " (" + vehicleId + ")");
            imageMarker.setSnippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName);
            textMarker.setSnippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName);
            if (idCurrentlySelected != null && idCurrentlySelected.equals(busMarker.getId()) && stopIdForNextStopInfoBoxCurrentlyOpen == null) {
                textMarker.showInfoWindow();
            }
        };
        actionHandler.postDelayed(runnable, waitTime);
    }

    private void setInfoWindowAdapter() {
        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            @Override
            public View getInfoWindow(Marker arg0) {
                return null;
            }

            @Override
            public View getInfoContents(Marker marker) {

                Context context = getApplicationContext();

                LinearLayout info = new LinearLayout(context);
                info.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(context);
                title.setTextColor(Color.BLACK);
                title.setGravity(Gravity.CENTER);
                title.setTypeface(null, Typeface.BOLD);
                title.setText(marker.getTitle());

                TextView snippet = new TextView(context);
                snippet.setTextColor(Color.GRAY);
                snippet.setText(marker.getSnippet());

                info.addView(title);
                info.addView(snippet);

                return info;
            }
        });
    }

    //Code taken from here; https://stackoverflow.com/questions/36510872/setmylocationenabled-error-on-android-6
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);

            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        mMap.setMyLocationEnabled(true);
                    }
                } else {
                    Toast.makeText(this, R.string.afterLocationPermissionDenied, Toast.LENGTH_LONG).show();
                }
            }

        }
    }
}