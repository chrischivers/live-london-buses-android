package io.chiv.livelondonbuses.utils;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MapUtils {

    public static LatLngBounds getWidenedBounds(LatLngBounds latLngBounds) {
        Double expansionFactor = 1.3; //TODO put in config
        Double southwestLat = latLngBounds.southwest.latitude;
        Double southwestLng = latLngBounds.southwest.longitude;
        Double northeastLat = latLngBounds.northeast.latitude;
        Double northeastLng = latLngBounds.northeast.longitude;

        Double latExpand = ((northeastLat - southwestLat) * expansionFactor) / 2;
        Double lngExpand = ((northeastLng - southwestLng) * expansionFactor) / 2;
        LatLng expandedSouthWest = new LatLng(southwestLat - latExpand, southwestLng - lngExpand);
        LatLng expandedNorthEast = new LatLng(northeastLat + latExpand, northeastLng + lngExpand);
        return new LatLngBounds(expandedSouthWest, expandedNorthEast);
    }

    public static JSONObject getLatLngBoundsJsonObj(LatLngBounds bounds) {

        JSONObject latLngBoundsObj = new JSONObject();
        try {
            JSONObject southwestObj = new JSONObject();
            JSONObject northeastObj = new JSONObject();
            southwestObj.put("lat", bounds.southwest.latitude);
            southwestObj.put("lng", bounds.southwest.longitude);
            northeastObj.put("lat", bounds.northeast.latitude);
            northeastObj.put("lng", bounds.northeast.longitude);

            latLngBoundsObj.put("southwest", southwestObj);
            latLngBoundsObj.put("northeast", northeastObj);
            return latLngBoundsObj;
        } catch (JSONException e) {
            e.printStackTrace();
            return latLngBoundsObj; //TODO is there a better approach?
        }
    }

    public static JSONArray getSelectedRoutesJsonArray(List<String> selectedRoutes) {

        JSONArray busRouteArray = new JSONArray();
        try {

            for (int i = 0; i < selectedRoutes.size(); i++) {
                JSONObject outboundObj = new JSONObject();
                JSONObject inboundObj = new JSONObject();

                outboundObj.put("id", selectedRoutes.get(i));
                outboundObj.put("direction", "outbound");
                inboundObj.put("id", selectedRoutes.get(i));
                inboundObj.put("direction", "inbound");
                busRouteArray.put(outboundObj);
                busRouteArray.put(inboundObj);
            }
            return busRouteArray;
        } catch (JSONException e) {
            e.printStackTrace();
            return busRouteArray; //TODO is there a better approach?
        }
    }

    public static String getFilteringParamsJsonStr(JSONObject latLngBoundsObj, JSONArray selectedRoutesArray) {
        JSONObject filteringParamsObj = new JSONObject();
        try {
            filteringParamsObj.put("busRoutes", selectedRoutesArray);
            filteringParamsObj.put("latLngBounds", latLngBoundsObj);
            return filteringParamsObj.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return filteringParamsObj.toString(); //TODO is there a better approach?
        }
    }
}
