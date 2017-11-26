package io.chiv.livelondonbuses.map;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.chiv.livelondonbuses.BuildConfig;
import io.chiv.livelondonbuses.R;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

class ServerClient {

    private static final String TAG = "ServerClient";
    private Context context;
    private OkHttpClient httpClient;
    private WebSocketClient wsClient;
    private static String baseUrl;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private boolean usingHttpPolling = false;
    private int HTTP_POLLING_INTERVAL_MS = 10000;
    private Timer timer;

    private ServerClient(Context context, Map map) {
        this.context = context;
         baseUrl = context.getResources().getString(R.string.mainServer);
        httpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    static ServerClient newInstance(Context context, Map map) {
        return new ServerClient(context, map);
    }

    void openDataStream(String uuid, Map map, String filteringParams) {
        if (!usingHttpPolling) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Opening websocket for " + uuid);
            openWebsocket(uuid, map, filteringParams);
        } else {
            if (BuildConfig.DEBUG) Log.i(TAG, "Starting http polling for " + uuid);
            switchOverToHttpPolling(uuid, map, filteringParams);
        }
    }

    void closeDataStream(String reason) {
        if (!usingHttpPolling) {
            closeWebsocket(WebSocketClient.WEBSOCKET_NORMAL_CLOSE_CODE, reason);
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    void switchOverToHttpPolling(String uuid, Map map, String filteringParams) {
        if (!usingHttpPolling) {
            wsClient.closeWebsocket(WebSocketClient.WEBSOCKET_SWITCHOVER_CLOSE_CODE, "Switching over to http");
            timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    getPositionDataOverHttp(uuid, map);
                }
            }, HTTP_POLLING_INTERVAL_MS, HTTP_POLLING_INTERVAL_MS);
            usingHttpPolling = true;

            if (filteringParams != null) {
                updateParamsAndGetSnapshot(uuid, filteringParams, map);
            }
        }
    }

    private void closeWebsocket(Integer closeCode, String reason) {
        if (wsClient != null) {
            wsClient.closeWebsocket(closeCode, reason);
            wsClient = null;
        }
    }

    private void openWebsocket(String uuid, Map map, String filteringParams) {
        wsClient = new WebSocketClient(this, baseUrl, map);
        wsClient.run(uuid, filteringParams);
    }


    void getPositionDataOverHttp(String uuid, Map map) {

        String path = "/positions?uuid=" + uuid;

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errStr = "Error: Unable to retrieve position data from server";
                if (BuildConfig.DEBUG) Log.e(TAG, errStr, e);
                Toast.makeText(context, errStr, Toast.LENGTH_LONG).show();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBodyAsString = response.body().string();
                try {
                    JSONArray json = new JSONArray(responseBodyAsString);
                    map.runOnUiThread(() -> map.handlePositionJson(json));

                } catch (JSONException e) {
                    String errStr = "Error: Unable to decode position data from server [" + responseBodyAsString + "]";
                    if (BuildConfig.DEBUG) Log.e(TAG, errStr + e.getMessage(), e);
                    call.cancel();
                }
            }
        });
    }

    void getNextStopsMarkerAndPolylineData(String id, String vehicleId, String routeId, String direction, Map map, String autoOpenForStopId) {

        String path = "/nextstops?vehicleId=" + vehicleId + "&routeId=" + routeId + "&direction=" + direction;

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errStr = "Error: Unable to retrieve next stops data from server";
                if (BuildConfig.DEBUG) Log.e(TAG, errStr, e);
                Toast.makeText(context,
                        errStr,
                        Toast.LENGTH_LONG).show();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBodyAsString = response.body().string();
                try {
                    JSONArray json = new JSONArray(responseBodyAsString);
                    map.runOnUiThread(() -> map.onNextStopMarkerAndPolylineJsonReceived(id, json, autoOpenForStopId));

                } catch (JSONException e) {
                    String errStr = "Error: Unable to process next stops data from server [" + responseBodyAsString + "]";
                    if (BuildConfig.DEBUG) Log.e(TAG, errStr + e.getMessage(), e);
                    call.cancel();
                }
            }
        });
    }

    void updateRouteList(final Map map) {

        String path = "/routelist";

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errStr = "Error: Unable to connect to server to retrieve route list";
                if (BuildConfig.DEBUG) Log.e(TAG, errStr, e);
                Toast.makeText(context,
                        errStr,
                        Toast.LENGTH_LONG).show();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                final ArrayList<String> routeList = new ArrayList<>();
                final String responseBodyAsString = response.body().string();

                try {
                    JSONArray json = new JSONArray(responseBodyAsString);
                    for (int i = 0; i < json.length(); i++) {
                        routeList.add(json.getString(i));
                    }
                    map.runOnUiThread(() -> map.onRouteListUpdated(routeList));
                } catch (JSONException e) {
                    String errStr = "Error: Unable to process route list from server [" + responseBodyAsString + "]";
                    if (BuildConfig.DEBUG) Log.e(TAG, errStr, e);
                    call.cancel();
                }
            }
        });
    }

    void updateParamsAndGetSnapshot(String uuid, String filterParamsJsonStr, final Map map) {

        String path = "/snapshot?uuid=" + uuid;

        RequestBody requestBody = RequestBody.create(JSON, filterParamsJsonStr);

        Request request = new Request.Builder()
                .url("http://" + baseUrl + path)
                .post(requestBody)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String errStr = "Error: Unable to retrieve data from server";
                if (BuildConfig.DEBUG) Log.e(TAG, errStr, e);
                Toast.makeText(context,
                        errStr,
                        Toast.LENGTH_LONG).show();
                call.cancel();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBodyAsString = response.body().string();
                try {
                    JSONArray json = new JSONArray(responseBodyAsString);
                    map.runOnUiThread(() -> map.onSnapshotReceived(json));

                } catch (JSONException e) {
                    String errStr = "Error: Unable to process data from server [" + responseBodyAsString + "]";
                    if (BuildConfig.DEBUG) Log.e(TAG, errStr + e.getMessage(), e);
                    call.cancel();
                }
            }
        });
    }
}


