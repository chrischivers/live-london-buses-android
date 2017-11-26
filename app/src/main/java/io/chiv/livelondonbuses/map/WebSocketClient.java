package io.chiv.livelondonbuses.map;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class WebSocketClient extends WebSocketListener {

    private static final String TAG = "WebSocketClient";
    public static final Integer WEBSOCKET_NORMAL_CLOSE_CODE = 1000;
    public static final Integer WEBSOCKET_SWITCHOVER_CLOSE_CODE = 4000;
    private String baseUrl;
    private Map map;

    private ServerClient serverClient;
    private WebSocket webSocket;
    private String uuid;

    WebSocketClient(ServerClient serverClient, String baseUrl, Map map) {
        this.serverClient = serverClient;
        this.baseUrl = baseUrl;
        this.map = map;
    }

    void run(String uuid, String filteringParamsJson) {
        this.uuid = uuid;

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Request request = new Request.Builder()
                .url("ws://" + baseUrl + "/ws?uuid=" + uuid)
                .build();
        webSocket = client.newWebSocket(request, this);
        if (filteringParamsJson != null) {
            sendFilteringParams(filteringParamsJson);
        }
        client.dispatcher().executorService().shutdown();

    }

    void closeWebsocket(Integer closeCode, String reason) {
        Log.i(TAG, "Closing websocket with code " + closeCode + " because: " + reason);
        sendCloseMessage();
        webSocket.close(closeCode, reason);
        webSocket = null;
    }

    private void sendFilteringParams(String filteringParams) {
        Log.d(TAG,"Websocket sending filtering parameters: [" + filteringParams + "]");
        webSocket.send((filteringParams));
    }

    private void sendCloseMessage() {
        Log.d(TAG,"Websocket sending close message");
        webSocket.send("CLOSE");
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.i(TAG,"Websocket opened");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (webSocket != null) {
            try {
                JSONArray jsonArray = new JSONArray(text);
                map.runOnUiThread(() -> map.onPositionJsonReceived(jsonArray));
            } catch (JSONException e) {
                Log.e(TAG, "Error: Invalid json packet received from server [" + text + "]", e);
            }
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(WEBSOCKET_NORMAL_CLOSE_CODE, reason);
        Log.i(TAG,"Closing websocket with code " + code + " and reason " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
             //EOF exception is expected due to websocket server implementation
        if (!t.getClass().equals(java.io.EOFException.class)) {
            Log.e(TAG, "Websocket failure. Switching over to http", t);
            serverClient.switchOverToHttpPolling(uuid, map, null);
        }

    }
}