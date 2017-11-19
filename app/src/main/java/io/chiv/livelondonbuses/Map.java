package io.chiv.livelondonbuses;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;


public class Map extends FragmentActivity implements OnMapReadyCallback, MapCallbacks{

    private GoogleMap mMap;
    private ServerClient serverClient;
    private List<String> selectedRoutes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void addRouteSelectionButton() {
        Button button = new Button(this);
        button.setText(R.string.selectRoutes);
        addContentView(button, new ActionBar.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT));

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRouteSelector();
            }
        });
    }

    private void showRouteSelector() {

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);
        RouteSelector.newInstance().show(ft, "dialog");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        serverClient = ServerClient.newInstance(getApplicationContext());
        serverClient.updateRouteList(this);

        // Add a marker in Sydney and move the camera
        LatLng london = new LatLng(51.505485, -0.127889);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(london, 11));
    }

    @Override
    public void onRouteListUpdated() {
        addRouteSelectionButton();
    }
}
