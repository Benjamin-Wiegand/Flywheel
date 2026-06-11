package io.benwiegand.projection.geargrinder;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.bluetooth.BluetoothClient;
import io.benwiegand.projection.geargrinder.exception.BluetoothConnectionException;
import io.benwiegand.projection.geargrinder.logs.LogUiAdapter;
import io.benwiegand.projection.geargrinder.logs.LogcatReader;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiInfoResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiStartRequest;

public class DebugActivity extends AppCompatActivity {
    private static final String TAG = DebugActivity.class.getSimpleName();

    // useful for debugging
    private static final boolean AUTOSTART_TCP_SERVER = false;

    private LogcatReader logcatReader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_debug);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.log_marker_button).setOnClickListener(v -> logcatReader.addMarker());

        findViewById(R.id.toggle_recording_button).setOnClickListener(v -> {
            if (logcatReader.isRecording()) {
                Throwable error = logcatReader.stopRecording();
                if (error == null) {
                    Toast.makeText(this, "recording stopped", Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.e(TAG, "error during recording", error);
                new AlertDialog.Builder(this)
                        .setTitle("Log recording error")
                        .setMessage("an error happened during the recording:\n\n" + error.getClass().getSimpleName() + ": " + error.getMessage())
                        .setPositiveButton("close", null)
                        .show();
                return;
            }

            EditText et = new EditText(this);
            new AlertDialog.Builder(this)
                    .setTitle("recording name")
                    .setView(et)
                    .setPositiveButton("start", (d, i) -> {
                        String name = et.getText() + ".log";
                        Log.d(TAG, "starting log recording to: " + name);
                        File logFile = getFilesDir().toPath().resolve(name).toFile();
                        try {
                            logcatReader.startRecording(logFile);
                            Toast.makeText(this, "recording started", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e(TAG, "failed to start recording", e);
                        }
                    })
                    .setNegativeButton("cancel", null)
                    .setCancelable(false)
                    .show();

        });

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        RecyclerView logRecyclerView = findViewById(R.id.log_recycler);
        LogUiAdapter logUiAdapter = new LogUiAdapter();

        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logUiAdapter);
        logRecyclerView.setItemAnimator(null);  // does not work with fast-paced logs

        logUiAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                logRecyclerView.scrollToPosition(positionStart + itemCount - 1);
            }
        });

        logcatReader = new LogcatReader(logUiAdapter);
        logcatReader.start();

        if (AUTOSTART_TCP_SERVER)
            startService(new Intent(this, ConnectionService.class)
                    .setAction(ConnectionService.INTENT_ACTION_START_TCP));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logcatReader.destroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_debug, menu);
        return super.onCreateOptionsMenu(menu);
    }

    private void connectBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "missing bluetooth permission");
                    ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 69);
                return;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "missing location permission (for bluetooth)");
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 69);
                return;
            }
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "no bluetooth adapter");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "bluetooth is turned off");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<BluetoothDevice> deviceList = List.copyOf(pairedDevices);
        String[] deviceNames = deviceList.stream()
                .map(device -> device.getName() + " [" + device.getAddress() + "]")
                .toArray(String[]::new);

        new AlertDialog.Builder(this)
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice selectedDevice = deviceList.get(which);
                    BluetoothClient client = new BluetoothClient(this, selectedDevice.getAddress(), new BluetoothClient.Listener() {
                        @Override
                        public void onStartWireless(WifiStartRequest connectionInfo, WifiInfoResponse wifiInfo) {
                            Log.i(TAG, "got request to start wireless");
                            startService(new Intent(DebugActivity.this, ConnectionService.class)
                                    .setAction(ConnectionService.INTENT_ACTION_START_WIRELESS)
                                    .putExtra(ConnectionService.INTENT_EXTRA_WIRELESS_CONNECTION_INFO, connectionInfo)
                                    .putExtra(ConnectionService.INTENT_EXTRA_WIRELESS_WIFI_INFO, wifiInfo));
                        }

                        @Override
                        public void onBluetoothConnectionError(Throwable t) {
                            Log.e(TAG, "bluetooth connection error", t);
//                            throw new BluetoothConnectionException(context, R.string.bluetooth_connection_error_general_failure, e);
                        }

                        @Override
                        public void onBluetoothDisconnected() {
                            Log.v(TAG, "bluetooth disconnected");
                        }
                    });

                    Log.i(TAG, "connecting to bluetooth");
                    try {
                        client.connect();
                    } catch (BluetoothConnectionException e) {
                        Log.e(TAG, "failed to start bluetooth connection", e);
                    }
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Map<Integer, Supplier<Boolean>> actionMap = Map.of(
                R.id.connect_bluetooth_button, () -> {
                    connectBluetooth();
                    return true;
                },
                R.id.start_tcp_server_button, () -> {
                    startService(new Intent(this, ConnectionService.class)
                            .setAction(ConnectionService.INTENT_ACTION_START_TCP));
                    return true;
                },
                R.id.force_start_service_button, () -> {
                    startService(new Intent(this, ConnectionService.class)
                            .setAction(ConnectionService.INTENT_ACTION_CONNECT_USB));
                    return true;
                },
                R.id.debug_launch_projection_button, () -> {
                    startActivity(new Intent(this, ProjectionActivity.class));
                    return true;
                },
                R.id.launch_privd_button, () -> {
                    startService(new Intent(this, PrivdService.class));
                    return true;
                },
                R.id.start_audio_capture_button, () -> {
                    startActivity(new Intent(this, ConnectionRequestActivity.class)
                            .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_MEDIA_PROJECTION));
                    return true;
                }
        );
        Supplier<Boolean> action = actionMap.getOrDefault(item.getItemId(), () -> super.onOptionsItemSelected(item));
        assert action != null;
        return action.get();
    }
}
