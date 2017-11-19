package io.chiv.livelondonbuses;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

import io.chiv.livelondonbuses.utils.MapUtils;


public class Map extends FragmentActivity implements OnMapReadyCallback, MapCallbacks {

    private static final String TAG = "MapActivity";
    private GoogleMap mMap;
    private ServerClient serverClient;
    private ArrayList<String> selectedRoutes = new ArrayList<>();
    private String uuid;

    @Override
    protected void onPause() {
        Log.i(TAG, "Activity Paused");
        if (serverClient != null) {
            serverClient.closeWebsocket("Application paused (probably in background)");
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Activity resumed");
        generateNewUUID();
        if (serverClient != null) {
            String filteringParams = getFilteringParams();
            serverClient.openWebsocket(uuid, this, filteringParams);
            serverClient.updateParamsAndGetSnapshot(uuid,  filteringParams, this);
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

        LatLng london = new LatLng(51.505485, -0.127889);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(london, 11));
    }

    private void addRouteSelectionButton(final ArrayList<String> routeList) {
        Button button = new Button(this);
        button.setText(R.string.selectRoutes);
        addContentView(button, new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT));

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRouteSelector(routeList);
            }
        });
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
        selectedRoutes = newlySelectedRoutes;
        serverClient.updateParamsAndGetSnapshot(uuid,  getFilteringParams(), this);
    }

    @Override
    public void onSnapshotReceived(JSONArray jsonArray) {
        //TODO check not already existing - if existing, ignore.
        System.out.println("Received snapshot data: " + jsonArray.toString());
    }

    @Override
    public void onjsonReceived(JSONArray jsonArray) {
        System.out.println("Received json data from websocket: " + jsonArray.toString());
    }

    LatLngBounds getBounds() {
        return mMap.getProjection().getVisibleRegion().latLngBounds;
    }

    void generateNewUUID() {
        uuid = UUID.randomUUID().toString();
    }

    String getFilteringParams() {
        JSONArray routesArray = MapUtils.getSelectedRoutesJsonArray(selectedRoutes);
        LatLngBounds expandedBounds = MapUtils.getWidenedBounds(getBounds());
        JSONObject latLngBoundsObj = MapUtils.getLatLngBoundsJsonObj(expandedBounds);
        return MapUtils.getFilteringParamsJsonStr(latLngBoundsObj, routesArray);
    }
}
