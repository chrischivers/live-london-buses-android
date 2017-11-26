package io.chiv.livelondonbuses.map;

import org.json.JSONArray;

import java.util.ArrayList;


public interface MapCallbacks {

    void onRouteListUpdated(ArrayList<String> routeList);

    void onRouteSelectionReturn(ArrayList<String> selectedRoutes);

    void onSnapshotReceived(JSONArray jsonArray);

    void onPositionJsonReceived(JSONArray jsonArray);

    void onNextStopMarkerAndPolylineJsonReceived(String id, JSONArray jsonArray, String autoOpenForNextStopId);
}
