package io.chiv.livelondonbuses;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;


public interface MapCallbacks {

    void onRouteListUpdated(ArrayList<String> routeList);

    void onRouteSelectionReturn(ArrayList<String> selectedRoutes);

    void onSnapshotReceived(JSONArray jsonArray);

    void onjsonReceived(JSONArray jsonArray);
}
