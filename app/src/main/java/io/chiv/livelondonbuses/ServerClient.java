package io.chiv.livelondonbuses;

import android.content.Context;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

class ServerClient {

    private Context context;
    private RequestQueue queue;
    private static String baseUrl ="http://buses.chiv.io";
    private static List<String> routeList = new ArrayList<>();

    private ServerClient(Context context) {
        this.context = context;
        this. queue = Volley.newRequestQueue(context);
    }

    static ServerClient newInstance(Context context) {
        return new ServerClient(context);
    }

    static List<String> getRouteList() {
        return routeList;
    }

    void updateRouteList(final MapCallbacks mapCallbacks) {

        String path = "/routelist";

        JsonArrayRequest jsonRequest = new JsonArrayRequest(baseUrl + path,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            for (int i = 0; i < response.length(); i++) {
                                routeList.add(response.getString(i));
                            }
                            mapCallbacks.onRouteListUpdated();
                        } catch (JSONException e){
                            e.printStackTrace();
                            Toast.makeText(context,
                                    "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                error.printStackTrace();
                Toast.makeText(context,
                        "Error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        queue.add(jsonRequest);

    }
}
