package io.chiv.livelondonbuses;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class WebSocketClient extends WebSocketListener {

    private static final String TAG = "WebSocketClient";
    private static final Integer WEBSOCKET_NORMAL_CLOSE_CODE = 1000;
    private String baseUrl;
    private Map map;

    private WebSocket webSocket;

    WebSocketClient(String baseUrl, Map map) {
        this.baseUrl = baseUrl;
        this.map = map;
    }

    void run(String uuid, String filteringParamsJson) {

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

    void closeWebsocket(String reason) {
        Log.i(TAG, "Closing websocket because: " + reason);
        sendCloseMessage();
        webSocket.close(WEBSOCKET_NORMAL_CLOSE_CODE, reason);
        webSocket = null;
    }

    private void sendFilteringParams(String filteringParams) {
        System.out.println("Websocket sending message: " + filteringParams);
        webSocket.send((filteringParams));
    }

    private void sendCloseMessage() {
        System.out.println("Websocket sending close message");
        webSocket.send("CLOSE");
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        System.out.println("Websocket opened");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (webSocket != null) {
            System.out.println("MESSAGE: " + text);
            try {
                JSONArray jsonArray = new JSONArray(text);
                map.runOnUiThread(() -> map.onJsonReceived(jsonArray));
            } catch (JSONException e) {
                Log.e(TAG, "Invalid json packet received: " + text);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        if (webSocket != null) {
            System.out.println("MESSAGE: " + bytes.hex());
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        webSocket.close(WEBSOCKET_NORMAL_CLOSE_CODE, reason);
        System.out.println("CLOSE: " + code + " " + reason);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        t.printStackTrace();
    }
}