package io.chiv.livelondonbuses;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.chiv.livelondonbuses.models.BusMarker;
import io.chiv.livelondonbuses.utils.MapUtils;
import io.chiv.livelondonbuses.utils.MarkerAnimation;


public class Map extends FragmentActivity implements OnMapReadyCallback, MapCallbacks {

    private static final String TAG = "MapActivity";
    private GoogleMap mMap;
    private ServerClient serverClient;
    private List<String> selectedRoutes = Collections.synchronizedList(new ArrayList<>());
    private BusMarker markerOfInfoWindowCurrentlyOpen;
    private String uuid;

    //TODO DEAL with switchover to HTTP if websockets fail
    private ConcurrentHashMap<String, BusMarker> markerMap = new ConcurrentHashMap<>();

    private Handler actionHandler = new Handler();
    private Timer timer = new Timer();
    private IconGenerator iconFactory;

    @Override
    protected void onPause() {
        Log.i(TAG, "Activity Paused");
        if (serverClient != null) {
            serverClient.closeWebsocket("Application paused (probably in background)");
        }
        clearMap();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Activity resumed");
        generateNewUUID();
        if (serverClient != null) {
            String filteringParams = getFilteringParams();
            serverClient.openWebsocket(uuid, this, filteringParams);
            serverClient.updateParamsAndGetSnapshot(uuid, filteringParams, this);
        }
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        serverClient = ServerClient.newInstance(getApplicationContext(), this);
        serverClient.updateRouteList(this);
        serverClient.openWebsocket(uuid, this, null);
        setUpIconFactory();
        setCleanUpTimer();
        setInfoWindowAdapter();

        mMap.getUiSettings().setRotateGesturesEnabled(false);
        LatLng london = new LatLng(51.505485, -0.127889);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(london, 11));

        mMap.setOnCameraIdleListener(() -> {
            Log.i(TAG, "On camera idle firing");
            onBoundsChange(MapUtils.getWidenedBounds(getBounds()));
        });

        mMap.setOnMarkerClickListener(marker -> {
            String id = (String)marker.getTag();
            markerOfInfoWindowCurrentlyOpen = markerMap.get(id);
            return false;
        });

        mMap.setOnInfoWindowCloseListener(marker -> markerOfInfoWindowCurrentlyOpen = null);
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
        iconFactory = new IconGenerator(this);
        iconFactory.setBackground(new ColorDrawable(Color.TRANSPARENT));
        iconFactory.setTextAppearance(R.style.markerText);
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
        RouteSelector.newInstance(routeList).show(ft, "dialog");
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
        System.out.println("Received snapshot data: " + jsonArray.toString());

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
                //TODO logging etc.
                e.printStackTrace();
            }
        }
        handleJson(filteredJsonArray);
    }

    @Override
    public void onJsonReceived(JSONArray jsonArray) {
        System.out.println("Received json data from websocket: " + jsonArray.toString());
        handleJson(jsonArray);
    }


    private void handleJson(JSONArray jsonArray) {
        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObj = jsonArray.getJSONObject(i);
                String routeId = jsonObj.getJSONObject("busRoute").getString("id");
                String direction = jsonObj.getJSONObject("busRoute").getString("direction");
                String vehicleId = jsonObj.getString("vehicleId");
                Integer nextStopIndex = jsonObj.getInt("nextStopIndex");
                String id = getId(vehicleId, routeId, direction);
                Log.i(TAG, "Json received for id " + id);

                if (isReceivedRouteInSelected(routeId) && isReceivedIndexAheadOfLastHandled(nextStopIndex, id)) {
                    Long startingTime = jsonObj.getLong("startingTime");
                    LatLng startingLatLng = latLngFrom(jsonObj.getJSONObject("startingLatLng"));
                    Boolean deleteAfter = jsonObj.getBoolean("deleteAfter");
                    Long nextStopArrivalTime = getOrElse(jsonObj.getLong("nextStopArrivalTime"), System.currentTimeMillis() + 90000);
                    String nextStopName = jsonObj.getString("nextStopName");

                    JSONArray movementInstructions = jsonObj.getJSONArray("movementInstructionsToNext");
                    String destination = jsonObj.getString("destination");

                    deleteExistingMarkerIfClientRunningBehind(id, deleteAfter, nextStopIndex);

                    if (!markerMap.containsKey(id)) {
                        markerMap.put(id, createBusMarker(id, routeId, startingLatLng, 0, nextStopName, vehicleId, destination));
//                    createBusMarkerInfoBoxListener(id);
//                    createBusMarkerNextStopsListener(id, vehicleId, routeId, direction);
//                    animationInstructionsMap[id] = [];
                    }

                    BusMarker busMarker = markerMap.get(id);

                    Long now = System.currentTimeMillis();
                    Long timeNextFree = getOrElse(busMarker.getTimeNextFree(), 0L);
                    Long adjustedStartingTime = startingTime < timeNextFree ? timeNextFree : startingTime;
                    Long waitTimeBeforeStart = adjustedStartingTime - now;
                    Long timeAccumulator = waitTimeBeforeStart < 0 ? 0 : waitTimeBeforeStart;
                    Long realTimeToNextStop = nextStopArrivalTime - (adjustedStartingTime < now ? now : adjustedStartingTime);
                    Long timeToNextStop = realTimeToNextStop > 0 ? realTimeToNextStop : 5000;

                    createOrUpdateBusInfoBox(busMarker, routeId, nextStopName, vehicleId, destination, timeAccumulator);

                    //TODO
//                if (typeof idOfNextStopMarkersSelected !== 'undefined') {
//                    if (idOfNextStopMarkersSelected === id) {
//                        var autoOpenForStopId = idOfNextStopInfoBoxCurrentlyOpen;
//                        closeInfoBoxesForNextStopMarkers();
//                        createNextStopsMarkers(id, vehicleId, routeId, direction, autoOpenForStopId);
//                    }
//                }

                    for (int j = 0; j < movementInstructions.length(); j++) {
                        JSONObject movementJsonObj = movementInstructions.getJSONObject(j);
                        LatLng latLngToMoveTo = latLngFrom(movementJsonObj.getJSONObject("to"));
                        Integer angle = movementJsonObj.getInt("angle");
                        Double proportionalDistance = movementJsonObj.getDouble("proportion");
                        Integer proportionalTime = Double.valueOf(timeToNextStop * proportionalDistance).intValue();

                        animateMarker(busMarker, proportionalTime, latLngToMoveTo, angle, timeAccumulator);
                        timeAccumulator += proportionalTime;
                    }

                    if (deleteAfter) {
                        deleteBusMarker(id, timeAccumulator);
                    }

                    busMarker.setTimeNextFree(nextStopArrivalTime);
                    busMarker.setLastHandledNextIndex(nextStopIndex);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private LatLng latLngFrom(JSONObject latLngObj) throws JSONException {
        return new LatLng(latLngObj.getDouble("lat"),
                latLngObj.getDouble("lng"));
    }

    private String getId(String vehicleId, String routeId, String direction) {
        return vehicleId + "-" + routeId + "-" + direction;
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

    private <T> T getOrElse(T maybeNull, T valueIfNull) {
        if (maybeNull == null) {
            return valueIfNull;
        } else {
            return maybeNull;
        }
    }

    private BusMarker createBusMarker(String id, String routeId, LatLng latLng, Integer rotation, String nextStopName, String vehicleId, String destination) {

        //TODO is rotation being initially set to 0 correct?

        MarkerOptions imageMarkerOptions = new MarkerOptions()
                .position(new LatLng(latLng.latitude + 0.000005, latLng.longitude + 0.000005)) // Offset necessary to avoid flickering markers in UI
                .rotation(rotation)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.busicon))
                .title("Route " + routeId + " (" + vehicleId + ")")
                .snippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName)
                .anchor(0.5f, 0.5f)
                .visible(false);

        MarkerOptions textMarkerOptions = new MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(routeId)))
                .position(latLng)
                .title("Route " + routeId + " (" + vehicleId + ")")
                .snippet("Towards " + destination + "\n" + "Next Stop: " + nextStopName)
                .anchor(0.5f, 0.5f)
                .visible(false);

        Marker imageMarker = mMap.addMarker(imageMarkerOptions);
        imageMarker.setTag(id);
        imageMarker.setInfoWindowAnchor(0.5f, 0.5f);
        Marker textMarker = mMap.addMarker(textMarkerOptions);
        textMarker.setTag(id);
        textMarker.setInfoWindowAnchor(0.5f, 0.5f);

        return new BusMarker(imageMarker, textMarker);

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
        JSONArray routesArray = MapUtils.getSelectedRoutesJsonArray(selectedRoutes);
        LatLngBounds expandedBounds = MapUtils.getWidenedBounds(getBounds());
        JSONObject latLngBoundsObj = MapUtils.getLatLngBoundsJsonObj(expandedBounds);
        return MapUtils.getFilteringParamsJsonStr(latLngBoundsObj, routesArray);
    }


    private void clearRoutes(List<String> routesToClear) {
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            for (int i = 0; i < routesToClear.size(); i++) {
                if (entry.getKey().contains("-" + routesToClear.get(i) + "-")) {
                    deleteBusMarker(entry.getKey(), 0L);
                }
            }
        }
    }

    private void clearMap() {
        for (HashMap.Entry<String, BusMarker> entry : markerMap.entrySet()) {
            deleteBusMarker(entry.getKey(), 0L);
//            closeInfoBoxesForNextStopMarkers();
//            deleteNextStopMarkers();
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
                deleteBusMarker(entry.getKey(), 0L);
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
            if (markerOfInfoWindowCurrentlyOpen != null && markerOfInfoWindowCurrentlyOpen.equals(imageMarker)) {
               //TODO
                imageMarker.showInfoWindow();
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
}
/*


     var stopIcon = "assets/images/mm_20_orange.png";
                var nextStopsMarkersSelected = [];
                var idOfNextStopMarkersSelected;
                var nextStopInfoBoxCurrentlyOpen;
                var idOfNextStopInfoBoxCurrentlyOpen;

                function createBusMarkerNextStopsListener(id, vehicleId, routeId, direction) {
                    google.maps.event.addListener(markerArray[id], 'click', function () {
                        var autoOpenForStopId = idOfNextStopInfoBoxCurrentlyOpen;
                        closeInfoBoxesForNextStopMarkers();
                        createNextStopsMarkers(id, vehicleId, routeId, direction, autoOpenForStopId);
                    })
                }


                function createNextStopsMarkers(id, vehicleId, routeId, direction, autoOpenForStopId) {
                    $.get("/nextstops?vehicleId=" + vehicleId + "&routeId=" + routeId + "&direction=" + direction,
                            function (data, status) {
                                var nextStopsJson = JSON.parse(data);
                                var tempNextStopsMarkersSelected = [];
                                for (var i = 0; i < nextStopsJson.length; i++) {

                                    var stopId = nextStopsJson[i].busStop.stopID;
                                    var stopName = nextStopsJson[i].busStop.stopName;
                                    var predictedArrival = nextStopsJson[i].predictedArrival;
                                    var latLng = nextStopsJson[i].busStop.latLng;

                                    var nextStopMarker = new google.maps.Marker({
                                        position: latLng,
                                        map: map,
                                        icon: stopIcon
                                    });

                                    tempNextStopsMarkersSelected.push(nextStopMarker);
                                    createNextStopMarkerListener(nextStopMarker, stopId, stopName, predictedArrival);
                                    if (typeof autoOpenForStopId !== 'undefined' && autoOpenForStopId === stopId) {
                                        google.maps.event.trigger(nextStopMarker, 'mouseover');
                                    }
                                }
                                deleteNextStopMarkers();
                                nextStopsMarkersSelected = tempNextStopsMarkersSelected;
                                idOfNextStopMarkersSelected = id;
                            });
                }


   function closeInfoBoxesForNextStopMarkers() {
                    if (typeof nextStopInfoBoxCurrentlyOpen !== 'undefined') {
                        nextStopInfoBoxCurrentlyOpen.close();
                        nextStopInfoBoxCurrentlyOpen = void 0;
                        idOfNextStopInfoBoxCurrentlyOpen = void 0;
                    }
                }

                function deleteNextStopMarkers() {
                    for (var i = 0; i < nextStopsMarkersSelected.length; i++) {
                        nextStopsMarkersSelected[i].setMap(null);
                    }
                    nextStopsMarkersSelected = [];
                }
 */
