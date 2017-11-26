package io.chiv.livelondonbuses.models;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.chiv.livelondonbuses.utils.JsonHelpers;
import io.chiv.livelondonbuses.utils.MapUtils;


public class PositionData {

    public String getRouteId() {
        return routeId;
    }

    public String getDirection() {
        return direction;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public int getNextStopIndex() {
        return nextStopIndex;
    }

    public long getStartingTime() {
        return startingTime;
    }

    public LatLng getStartingLatLng() {
        return startingLatLng;
    }

    public boolean isDeleteAfter() {
        return deleteAfter;
    }

    public long getNextStopArrivalTime() {
        return nextStopArrivalTime;
    }

    public String getNextStopName() {
        return nextStopName;
    }

    public JSONArray getMovementInstructions() {
        return movementInstructions;
    }

    public String getDestination() {
        return destination;
    }

    public String getId() {
        return id;
    }

    private String routeId;
    private String direction;
    private String vehicleId;
    private int nextStopIndex;
    private long startingTime;
    private LatLng startingLatLng;
    private boolean deleteAfter;
    private long nextStopArrivalTime;
    private String nextStopName;
    private JSONArray movementInstructions;
    private String destination;

    private String id;


    public PositionData(JSONObject jsonObj) throws JSONException {
        this.routeId = jsonObj.getJSONObject("busRoute").getString("id");
        this.direction = jsonObj.getJSONObject("busRoute").getString("direction");
        this.vehicleId = jsonObj.getString("vehicleId");
        this.nextStopIndex = jsonObj.getInt("nextStopIndex");
        this.id = MapUtils.getId(vehicleId, routeId, direction);


        this.startingTime = jsonObj.getLong("startingTime");
        this.startingLatLng = JsonHelpers.latLngFrom(jsonObj.getJSONObject("startingLatLng"));
        this.deleteAfter = jsonObj.getBoolean("deleteAfter");
        this.nextStopArrivalTime = MapUtils.getOrElse(jsonObj.optLong("nextStopArrivalTime"), System.currentTimeMillis() + 90000);
        this.nextStopName = MapUtils.getOrElse(jsonObj.optString("nextStopName"), "N/A");

        this.movementInstructions = MapUtils.getOrElse(jsonObj.optJSONArray("movementInstructionsToNext"), new JSONArray());
        this.destination = jsonObj.getString("destination");

        this.id = MapUtils.getId(vehicleId, routeId, direction);
    }
}