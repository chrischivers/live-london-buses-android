package io.chiv.livelondonbuses;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class ServerClient {

    private static final String TAG = "ServerClient";
    private Context context;
    private OkHttpClient httpClient;
    private WebSocketClient wsClient;
    private static String baseUrl = "buses.chiv.io";
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");


    private ServerClient(Context context, Map map) {
        this.context = context;
        httpClient = new OkHttpClient();
    }

    static ServerClient newInstance(Context context, Map map) {
        return new ServerClient(context, map);
    }

    void closeWebsocket(String reason) {
        wsClient.closeWebsocket(reason);
        wsClient = null;
    }

    void openWebsocket(String uuid, Map map, String filteringParams) {
        wsClient = new WebSocketClient(baseUrl, map);
        wsClient.run(uuid, filteringParams);
    }

//    void sendWSMessage(String message) {
//        wsClient.sendMessage(message);
//    }

    void updateRouteList(final Map map) {

        String path = "/routelist";

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error requesting route list from server. Error: " + e.getMessage());
                e.printStackTrace();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final ArrayList<String> routeList = new ArrayList<>();
                final String responseBodyAsString = response.body().string();

                map.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            JSONArray json = new JSONArray(responseBodyAsString);
                            for (int i = 0; i < json.length(); i++) {
                                routeList.add(json.getString(i));
                            }
                            map.onRouteListUpdated(routeList);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            Toast.makeText(context,
                                    "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });

            }
        });
    }

    void updateParamsAndGetSnapshot(String uuid, String filterParamsJsonStr, final Map map) {

        System.out.println("Updating parameters using UUID: " + uuid);
        String path = "/snapshot?uuid=" + uuid;

        RequestBody requestBody = RequestBody.create(JSON, filterParamsJsonStr);

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error requesting snapshot from server. Error: " + e.getMessage());
                e.printStackTrace();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBodyAsString = response.body().string();
                try {
                    JSONArray json = new JSONArray(responseBodyAsString);
                    map.onSnapshotReceived(json);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}


