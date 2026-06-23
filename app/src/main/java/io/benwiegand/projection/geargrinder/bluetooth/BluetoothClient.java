package io.benwiegand.projection.geargrinder.bluetooth;

import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_INFO_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_INFO_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_START_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_START_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_VERSION_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AABTConstants.CMD_WIFI_VERSION_RESPONSE;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.util.UUID;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.BluetoothConnectionException;
import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiInfoResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiStartRequest;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiVersionRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.bt.WifiStartResponse;
import io.benwiegand.projection.geargrinder.proto.data.writable.bt.WifiVersionResponse;
import io.benwiegand.projection.geargrinder.transfer.BluetoothTransferInterface;

public class BluetoothClient {
    private static final String TAG = BluetoothClient.class.getSimpleName();

    private static final UUID AA_SOCKET_UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66");
    private static final int BUFFER_SIZE = 2048;

    private final Thread thread = new Thread(this::connectionLoop, "geargrinder bluetooth client thread");

    private final Context context;
    private final BluetoothManager btManager;
    private final BluetoothAdapter adapter;
    private final BluetoothDevice device;
    private final Listener listener;

    private WifiStartRequest connectionInfo = null;
    private WifiInfoResponse wifiInfo = null;

    public interface Listener {
        void onStartWireless(WifiStartRequest connectionInfo, WifiInfoResponse wifiInfo);
        void onBluetoothConnectionError(Throwable t);
        void onBluetoothDisconnected();
    }

    public BluetoothClient(Context context, String targetMac, Listener listener) {
        this.context = context;
        this.listener = listener;
        btManager = context.getSystemService(BluetoothManager.class);
        adapter = btManager.getAdapter();

        // if it's not ALL CAPS it explodes
        targetMac = targetMac.toUpperCase();

        device = adapter != null ? adapter.getRemoteDevice(targetMac) : null;
    }

    public void close() {
        thread.interrupt();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public void connect() throws BluetoothConnectionException {
        if (adapter == null) {
            Log.e(TAG, "no bluetooth adapter");
            throw new BluetoothConnectionException(context, R.string.bluetooth_connection_error_no_adapter);
        }

        if (!adapter.isEnabled()) {
            throw new BluetoothConnectionException(context, R.string.bluetooth_connection_error_off);
        }

        thread.start();
    }

    private void connectionLoop() {
        Log.i(TAG, "connection thread init");
        try (BluetoothSocket socket = device.createRfcommSocketToServiceRecord(AA_SOCKET_UUID)) {
            BluetoothTransferInterface transferInterface = new BluetoothTransferInterface(socket);

            try {
                socket.connect();
            } catch (IOException e) {
                 Log.e(TAG, "IOException while connecting bluetooth socket", e);
                 listener.onBluetoothConnectionError(e);
                 return;
            }

            AABTFrame frame = new AABTFrame(new byte[BUFFER_SIZE]);

            while (true) {
                transferInterface.readFrame(frame.getBuffer());

                switch (frame.getCommand()) {
                    case CMD_WIFI_START_REQUEST -> {
                        connectionInfo = WifiStartRequest.parse(frame.getBuffer(), AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());
                        Log.v(TAG, "wifi start request: " + connectionInfo);

                        frame
                                .setCommand(CMD_WIFI_INFO_REQUEST)
                                .setPayloadLength(0);   // no payload
                        Log.v(TAG, "sending wifi info request: " + frame);
                        transferInterface.sendFrame(frame.getBuffer(), 0, frame.getLength());

                        frame
                                .setCommand(CMD_WIFI_START_RESPONSE)
                                .copyPayload(new WifiStartResponse(0).serialize());
                        Log.v(TAG, "sending wifi start response: " + frame);
                        transferInterface.sendFrame(frame.getBuffer(), 0, frame.getLength());
                    }
                    case CMD_WIFI_INFO_RESPONSE -> {
                        wifiInfo = WifiInfoResponse.parse(frame.getBuffer(), AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());
                        Log.v(TAG, "wifi info response: " + wifiInfo);

                        if (wifiInfo == null || connectionInfo == null) {
                            Log.e(TAG, "incomplete connection info, can't start connection");
                            continue;
                        }

                        listener.onStartWireless(connectionInfo, wifiInfo);
                    }
                    case CMD_WIFI_VERSION_REQUEST -> {
                        WifiVersionRequest versionRequest = WifiVersionRequest.parse(frame.getBuffer(), AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());
                        Log.v(TAG, "wifi version request: " + versionRequest);
                        ProtoParser.debugDumpRecursive(frame.getBuffer(), AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());

                        frame
                                .setCommand(CMD_WIFI_VERSION_RESPONSE)
                                .copyPayload(new WifiVersionResponse(1, 1, "deadbeef", 0).serialize());

                        Log.v(TAG, "sending wifi version response: " + frame);
                        transferInterface.sendFrame(frame.getBuffer(), 0, frame.getLength());
                    }
                    default -> {
                        Log.w(TAG, "unknown command: " + frame.getCommand());
                        Log.d(TAG, "rx raw: " + hexDump(frame.getBuffer(), 0, frame.getLength()));
                        ProtoParser.debugDumpRecursive(frame.getBuffer(), AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());
                    }
                }
            }

        } catch (IOException e) {
            Log.w(TAG, "IOException in bluetooth connection loop", e);
        } finally {
            Log.i(TAG, "connection thread death");
            listener.onBluetoothDisconnected();
        }
    }

}
