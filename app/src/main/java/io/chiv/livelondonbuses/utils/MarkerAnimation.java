package io.chiv.livelondonbuses.utils;


import android.animation.ValueAnimator;

import com.google.android.gms.maps.model.LatLng;

import io.chiv.livelondonbuses.models.BusMarker;

public class MarkerAnimation {
   /* static void animateMarkerToGB(final MarkerPair markerPair, final LatLng finalPosition, final LatLngInterpolator latLngInterpolator, final long duration) {
        final LatLng startPosition = markerPair.imageMarker.getPosition();
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        final Interpolator interpolator = new AccelerateDecelerateInterpolator();
        final float durationInMs = duration;
        handler.post(new Runnable() {
            long elapsed;
            float t;
            float v;
            @Override
            public void run() {
                // Calculate progress using interpolator
                elapsed = SystemClock.uptimeMillis() - start;
                t = elapsed / durationInMs;
                v = interpolator.getInterpolation(t);
                LatLng interpolateResult = latLngInterpolator.interpolate(v, startPosition, finalPosition);
                markerPair.imageMarker.setPosition(interpolateResult);
                markerPair.textMarker.setPosition(interpolateResult);
                // Repeat till progress is complete.
                if (t < 1) {
                    // Post again 16ms later.
                    handler.postDelayed(this, 16);
                }
            }
        });
    }*/

    public static void animateMarkerToHC(final BusMarker busMarker, final LatLng finalPosition, final long duration) {
        final LatLngInterpolator latLngInterpolator = new LatLngInterpolator.Linear();
        final LatLng startPositionText = busMarker.getTextMarker().getPosition();
        final LatLng startPositionImage = busMarker.getImageMarker().getPosition();

        ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = animation.getAnimatedFraction();
                // Offset necessary to avoid flickering markers in UI
                LatLng newPositionImage = latLngInterpolator.interpolate(v, startPositionImage, new LatLng(finalPosition.latitude + 0.000005, finalPosition.longitude + 0.000005));
                LatLng newPositionText = latLngInterpolator.interpolate(v, startPositionText, finalPosition);
                busMarker.getTextMarker().setPosition(newPositionText);
                busMarker.getImageMarker().setPosition(newPositionImage);

            }
        });
        valueAnimator.setFloatValues(0, 1); // Ignored.
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

  /*  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    static void animateMarkerToICS(MarkerPair markerPair, LatLng finalPosition, final LatLngInterpolator latLngInterpolator, final long duration) {
        TypeEvaluator<LatLng> typeEvaluator = new TypeEvaluator<LatLng>() {
            @Override
            public LatLng evaluate(float fraction, LatLng startValue, LatLng endValue) {
                return latLngInterpolator.interpolate(fraction, startValue, endValue);
            }
        };
        Property<Marker, LatLng> property = Property.of(Marker.class, LatLng.class, "position");
        ObjectAnimator imageMarkerAnimator = ObjectAnimator.ofObject(markerPair.imageMarker, property, typeEvaluator, finalPosition);
        ObjectAnimator textMarkerAnimator = ObjectAnimator.ofObject(markerPair.textMarker, property, typeEvaluator, finalPosition);
        imageMarkerAnimator.setDuration(duration);
        textMarkerAnimator.setDuration(duration);
        imageMarkerAnimator.start();
        textMarkerAnimator.start();
    }*/
}