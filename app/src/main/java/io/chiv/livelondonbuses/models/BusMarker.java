package io.chiv.livelondonbuses.models;

import android.os.Handler;

import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;

public class BusMarker {

    private Marker imageMarker;
    private Marker textMarker;
    private Integer lastHandledNextIndex;
    private Long timeNextFree;
    private ArrayList<Runnable> animationInstructions = new ArrayList<>();

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



    public BusMarker(Marker imageMarker, Marker textMarker) {
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
