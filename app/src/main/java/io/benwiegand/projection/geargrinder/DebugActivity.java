package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.UiUtil.errorDialog;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.companion.ObservingDevicePresenceRequest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.exception.BluetoothConnectionException;
import io.benwiegand.projection.geargrinder.logs.LogUiAdapter;
import io.benwiegand.projection.geargrinder.logs.LogcatReader;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;

public class DebugActivity extends AppCompatActivity implements GeargrinderServiceConnector.ConnectionListener, LogcatReader.UiLogListener {
    private static final String TAG = DebugActivity.class.getSimpleName();

    // useful for debugging
    private static final boolean AUTOSTART_TCP_SERVER = false;

    private GeargrinderServiceConnector connector;
    private final LogUiAdapter logUiAdapter = new LogUiAdapter();
    private boolean autoScroll = true;

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

        findViewById(R.id.log_marker_button).setOnClickListener(v -> getLogcatReader().ifPresent(LogcatReader::addMarker));

        findViewById(R.id.toggle_recording_button).setOnClickListener(v -> getLogcatReader().ifPresent(logcatReader -> {
            if (logcatReader.isRecording()) {
                Throwable error = logcatReader.stopRecording();
                if (error != null) onRecordingError(error);
                return;
            }

            EditText et = new EditText(this);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.log_recording_start_dialog_title)
                    .setView(et)
                    .setPositiveButton(R.string.start_log_recording_button, (d, i) -> {
                        String name = et.getText() + ".log";
                        Log.d(TAG, "starting log recording to: " + name);
                        File logFile = getFilesDir().toPath().resolve(name).toFile();
                        try {
                            logcatReader.startRecording(logFile);
                        } catch (IOException e) {
                            Log.e(TAG, "failed to start recording", e);
                            onRecordingError(e);
                        }
                    })
                    .setNegativeButton(R.string.cancel_button, null)
                    .setCancelable(false)
                    .show();
        }));

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        SwitchCompat autoScrollSwitch = findViewById(R.id.auto_scroll_switch);
        autoScrollSwitch.setOnCheckedChangeListener((v, checked) -> autoScroll = checked);

        RecyclerView logRecyclerView = findViewById(R.id.log_recycler);
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logUiAdapter);
        logRecyclerView.setItemAnimator(null);  // does not work with fast-paced logs

        logUiAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                if (!autoScroll) return;
                logRecyclerView.scrollToPosition(positionStart + itemCount - 1);
            }
        });

        logUiAdapter.onLog(null, "connecting to log service...");

        connector = new GeargrinderServiceConnector(TAG, this, this);
        connector.bindLogService(BIND_AUTO_CREATE | BIND_IMPORTANT);

        if (AUTOSTART_TCP_SERVER)
            startService(new Intent(this, ConnectionService.class)
                    .setAction(ConnectionService.INTENT_ACTION_START_TCP));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getLogcatReader().ifPresent(logcatReader -> logcatReader.unregisterUiListener(logUiAdapter));
        connector.destroy();
    }

    private Optional<LogcatReader> getLogcatReader() {
        return connector.getLogBinder()
                .map(LogService.ServiceBinder::getLogcatReader);
    }

    public void updateRecordingStatus(boolean recording) {
        Button toggleRecordingButton = findViewById(R.id.toggle_recording_button);
        toggleRecordingButton.setText(recording ? R.string.stop_log_recording_button : R.string.start_log_recording_button);
    }

    @Override
    public void onLogServiceConnected(LogService.ServiceBinder binder) {
        binder.getLogcatReader().registerUiListener(logUiAdapter);
        binder.getLogcatReader().registerUiListener(this);
        findViewById(R.id.log_marker_button).setEnabled(true);
        findViewById(R.id.toggle_recording_button).setEnabled(true);
        updateRecordingStatus(binder.getLogcatReader().isRecording());
    }

    @Override
    public void onRecordingStart(File file) {
        runOnUiThread(() -> {
            updateRecordingStatus(true);
            Toast.makeText(this, R.string.log_recording_started_toast, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRecordingError(Throwable t) {
        runOnUiThread(() -> {
            Log.e(TAG, "error during recording", t);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.log_recording_error_dialog_title)
                    .setMessage("an error happened during the recording:\n\n" + t.getClass().getSimpleName() + ": " + t.getMessage())
                    .setPositiveButton(R.string.close_button, null)
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    public void onRecordingStop() {
        runOnUiThread(() -> {
            updateRecordingStatus(false);
            Toast.makeText(this, R.string.log_recording_stopped_toast, Toast.LENGTH_SHORT).show();
        });
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private AlertDialog.Builder createBluetoothDeviceSelectionDialog(Consumer<BluetoothDevice> onSelection) {

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "no bluetooth adapter");
            return errorDialog(this, new BluetoothConnectionException(this, R.string.bluetooth_connection_error_no_adapter));
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "bluetooth is turned off");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return errorDialog(this, new BluetoothConnectionException(this, R.string.bluetooth_connection_error_off));
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<BluetoothDevice> deviceList = List.copyOf(pairedDevices);
        String[] deviceNames = deviceList.stream()
                .map(device -> device.getName() + " [" + device.getAddress() + "]")
                .toArray(String[]::new);

        return new AlertDialog.Builder(this)
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice selectedDevice = deviceList.get(which);
                    onSelection.accept(selectedDevice);
                });

    }

    private boolean checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "missing bluetooth permission");
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 69);
                return true;
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "missing location permission (for bluetooth)");
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 69);
                return true;
            }
        }
        return false;
    }

    private void connectBluetooth() {
        if (checkBluetoothPermission()) return;

        createBluetoothDeviceSelectionDialog(selectedDevice -> {
            Log.i(TAG, "selected " + selectedDevice + " for manual bluetooth connection");
            startService(new Intent(this, ConnectionService.class)
                    .setAction(ConnectionService.INTENT_ACTION_CONNECT_BLUETOOTH)
                    .putExtra(ConnectionService.INTENT_EXTRA_BLUETOOTH_DEVICE_ADDRESS, selectedDevice.getAddress()));
        }).show();
    }

    private void associateCompanionDevice(String address) {
        Handler handler = new Handler(Looper.getMainLooper());
        CompanionDeviceManager deviceManager = getSystemService(CompanionDeviceManager.class);

        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder()
                        .setAddress(address)
                        .build())
                .setSingleDevice(true)
                .build();

        deviceManager.associate(pairingRequest, new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationPending(@NonNull IntentSender chooserLauncher) {
                try {
                    Log.i(TAG, "association pending");
                    chooserLauncher.sendIntent(DebugActivity.this, 0, null, null, handler);
                } catch (IntentSender.SendIntentException e) {
                    Log.e(TAG, "failed to send intent", e);
                }
            }

            @Override
            public void onAssociationCreated(@NonNull AssociationInfo associationInfo) {
                Log.v(TAG, "association created: " + associationInfo);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    deviceManager.startObservingDevicePresence(new ObservingDevicePresenceRequest.Builder()
                            .setAssociationId(associationInfo.getId())
                            .build());
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    deviceManager.startObservingDevicePresence(address);
                } else {
                    Log.e(TAG, "can't observe association: requires android 12+");
                }
            }

            @Override
            public void onFailure(CharSequence errorMessage) {
                Log.e(TAG, "device pairing failed: " + errorMessage);
            }
        }, handler);

    }

    private void selectCompanionDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.e(TAG, "automatic wireless connection only works on android 12+");
            return;
        }

        if (checkBluetoothPermission()) return;

        createBluetoothDeviceSelectionDialog(selectedDevice -> {
            Log.i(TAG, "selected device for association: " + selectedDevice);
            associateCompanionDevice(selectedDevice.getAddress());
        }).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Map<Integer, Supplier<Boolean>> actionMap = Map.of(
                R.id.pair_bluetooth_button, () -> {
                    selectCompanionDevice();
                    return true;
                },
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
