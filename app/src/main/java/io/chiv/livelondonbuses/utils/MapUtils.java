package io.chiv.livelondonbuses.utils;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.ui.IconGenerator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import io.chiv.livelondonbuses.R;

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

    public static BitmapDescriptor makeIconForRoute(IconGenerator iconFactory, String routeId, int style) {
        iconFactory.setBackground(new ColorDrawable(Color.TRANSPARENT));
        iconFactory.setTextAppearance(style);
        return BitmapDescriptorFactory.fromBitmap(iconFactory.makeIcon(routeId));
    }

    public static String getId(String vehicleId, String routeId, String direction) {
        return "{" +
                "\"vehicleId\":\"" + vehicleId + "\"," +
                "\"routeId\":\"" + routeId + "\"," +
                "\"direction\":\"" + direction + "\"" +
                "}";
    }

    public static <T> T getOrElse(T maybeNull, T valueIfNull) {
        if (maybeNull == null) {
            return valueIfNull;
        } else {
            return maybeNull;
        }
    }
}
