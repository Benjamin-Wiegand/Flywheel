package io.benwiegand.projection.geargrinder.connector;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.Socket;

import javax.net.SocketFactory;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiInfoResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiStartRequest;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.transfer.TcpTransferInterface;

public class AAWirelessConnector extends AAConnector {
    private static final String TAG = AAWirelessConnector.class.getSimpleName();

    private final Thread thread = new Thread(this::clientThread, "Geargrinder wireless connection thread");

    private final ConnectivityManager connectivityManager;

    private final WifiStartRequest connectionInfo;
    private final WifiInfoResponse wifiInfo;

    private SocketFactory socketFactory = null;

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.i(TAG, "car wifi available: " + network);
            socketFactory = network.getSocketFactory();
            thread.start();
        }

        @Override
        public void onUnavailable() {
            Log.i(TAG, "car wifi unavailable");
            listener.onConnectionError(new UserFriendlyException(context, R.string.car_connection_error, R.string.error_headunit_wifi_unavailable));
            if (thread.isAlive()) {
                Log.v(TAG, "stopping connection due to wifi drop");
                stop();
                return;
            }
            connectivityManager.unregisterNetworkCallback(this);
            listener.onConnectorDeath();
        }
    };

    public AAWirelessConnector(Context context, AAConnector.StateListener listener, ControlListener controlListener, ConnectionService.ServiceBinder connectionServiceBinder, SettingsManager settingsManager, WifiStartRequest connectionInfo, WifiInfoResponse wifiInfo) {
        super(context, listener, controlListener, connectionServiceBinder, settingsManager);
        this.connectionInfo = connectionInfo;
        this.wifiInfo = wifiInfo;
        connectivityManager = context.getSystemService(ConnectivityManager.class);
    }

    @Override
    public void stop() {
        thread.interrupt();
    }

    @Override
    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listener.onConnectingStatus(R.string.looking_for_car);

            if (!wifiInfo.supportsWpa2())
                Log.w(TAG, "wifi connection may not work, only WPA2-personal is currently supported");

            WifiNetworkSpecifier networkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setBssid(MacAddress.fromString(wifiInfo.bssid()))
                    .setSsid(wifiInfo.essid())
                    .setWpa2Passphrase(wifiInfo.psk())
                    .build();

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(networkSpecifier)
                    .build();

            connectivityManager.requestNetwork(networkRequest, networkCallback);
        } else {
            Log.e(TAG, "wireless connection currently requires android 10 or later");
            listener.onConnectionError(new UserFriendlyException(context, R.string.car_connection_error, R.string.error_wireless_unsupported_android));
            listener.onConnectorDeath();
        }
    }

    private void clientThread() {
        try {
            listener.onConnectingStatus(R.string.connecting_to_car);

            try (Socket socket = socketFactory.createSocket(connectionInfo.ipAddress(), connectionInfo.port())) {
                Log.i(TAG, "connected to " + socket.getRemoteSocketAddress());
                listener.onConnected();
                TcpTransferInterface transferInterface = new TcpTransferInterface(socket);
                connectionLoop(transferInterface);
                listener.onDisconnected();
            } catch (IOException e) {
                Log.e(TAG, "IOException while connecting to headunit tcp server", e);
                throw new UserFriendlyException(context, R.string.car_connection_unexpected_error, R.string.error_wireless_net_client_io, e);
            }

        } catch (UserFriendlyException e) {
            listener.onConnectionError(e);
        } finally {
            listener.onConnectorDeath();
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}
