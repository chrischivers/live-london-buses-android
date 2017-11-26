package io.chiv.livelondonbuses.models;

import android.os.Handler;

import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.ui.IconGenerator;

import java.util.ArrayList;

import io.chiv.livelondonbuses.utils.MapUtils;

public class BusMarker {

    private String id;
    private String routeId;
    private Marker imageMarker;
    private Marker textMarker;
    private Integer lastHandledNextIndex;
    private Long timeNextFree;
    private ArrayList<Runnable> animationInstructions = new ArrayList<>();

    public String getId() {return id; }
    public String getRoute() {return routeId;}
    public Marker getImageMarker() {
        return imageMarker;
    }
    public Marker getTextMarker() {
        return textMarker;
    }
    public Integer getLastHandledNextIndex() {
        return lastHandledNextIndex;
    }
    public Long getTimeNextFree() {
        return timeNextFree;
    }
    public ArrayList<Runnable> getAnimationInstructions() {
        return animationInstructions;
    }

    public void setLastHandledNextIndex(Integer lastHandledNextIndex) {
        this.lastHandledNextIndex = lastHandledNextIndex;
    }

    public void setTimeNextFree(Long timeNextFree) {
        this.timeNextFree = timeNextFree;
    }

    public void changeIconTextStyle(IconGenerator iconGenerator, int style) {
        textMarker.setIcon(MapUtils.makeIconForRoute(iconGenerator, routeId, style));
    }

    public BusMarker(String id, String routeId, Marker imageMarker, Marker textMarker) {
        this.id = id;
        this.routeId = routeId;
        this.imageMarker = imageMarker;
        this.textMarker = textMarker;
    }

    public void delete(Handler actionHandler) {
        textMarker.remove();
        imageMarker.remove();

        for (int i = 0; i < animationInstructions.size(); i++) {
            actionHandler.removeCallbacks(animationInstructions.get(i));
        }
       animationInstructions.clear();
    }
}
