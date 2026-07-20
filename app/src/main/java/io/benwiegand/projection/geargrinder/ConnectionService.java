package io.benwiegand.projection.geargrinder;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;

import io.benwiegand.projection.geargrinder.bluetooth.BluetoothClient;
import io.benwiegand.projection.geargrinder.connector.AAConnector;
import io.benwiegand.projection.geargrinder.connector.AATcpConnector;
import io.benwiegand.projection.geargrinder.connector.AAUsbConnector;
import io.benwiegand.projection.geargrinder.connector.AAWirelessConnector;
import io.benwiegand.projection.geargrinder.exception.BluetoothConnectionException;
import io.benwiegand.projection.geargrinder.exception.LiabilityAgreementOutdatedException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.notification.ConnectionNotificationService;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiInfoResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.bt.WifiStartRequest;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class ConnectionService extends Service implements ControlListener, AAConnector.StateListener, BluetoothClient.Listener {
    private static final String TAG = ConnectionService.class.getSimpleName();

    public static final String INTENT_ACTION_CONNECT_USB = "io.benwiegand.projection.geargrinder.USB_HEADUNIT_CONNECTED";
    public static final String INTENT_ACTION_START_TCP = "io.benwiegand.projection.geargrinder.START_TCP_SERVER";
    public static final String INTENT_ACTION_START_WIRELESS = "io.benwiegand.projection.geargrinder.START_WIRELESS";
    public static final String INTENT_ACTION_CONNECT_BLUETOOTH = "io.benwiegand.projection.geargrinder.CONNECT_BLUETOOTH";
    public static final String INTENT_ACTION_START_MEDIA_PROJECTION = "io.benwiegand.projection.geargrinder.START_MEDIA_PROJECTION";
    public static final String INTENT_ACTION_STOP_CONNECTION = "io.benwiegand.projection.geargrinder.STOP_CONNECTION";

    public static final String INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT = "projection_result";
    public static final String INTENT_EXTRA_WIRELESS_WIFI_INFO = "wifi_info";
    public static final String INTENT_EXTRA_WIRELESS_CONNECTION_INFO = "conn_info";
    public static final String INTENT_EXTRA_BLUETOOTH_DEVICE_ADDRESS = "address";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private final Object lock = new Object();

    private ConnectionNotificationService notificationService;
    private SettingsManager settingsManager;

    private AAConnector connector = null;
    private BluetoothClient bluetoothClient = null;
    private MediaProjection mediaProjection = null;
    private MediaProjectionRequestCallback mediaProjectionRequestCallback = null;

    private ProjectionService projectionService = null;
    private Object projectionGracePeriodToken = null;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "on create");

        notificationService = new ConnectionNotificationService(this);
        settingsManager = new SettingsManager(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "on destroy");
        if (connector != null) connector.stop();
        if (bluetoothClient != null) bluetoothClient.close();
        if (projectionService != null) projectionService.destroy();
        if (mediaProjection != null) mediaProjection.stop();
        notificationService.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.e(TAG, "intent is null");
            return START_NOT_STICKY;
        }

        Log.d(TAG, "start intent: " + intent);

        int liabilityVersion = settingsManager.getLiabilityAgreementVersion();
        if (liabilityVersion != SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT) {
            Log.w(TAG, "liability agreement version (" + liabilityVersion + ") != current (" + SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT + "), cancelling service start");
            notificationService.postError(new LiabilityAgreementOutdatedException(this).fillInStackTrace());
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case INTENT_ACTION_CONNECT_USB -> connectUsb();
            case INTENT_ACTION_START_TCP -> startTcpServer();
            case INTENT_ACTION_START_WIRELESS -> startWireless(intent);
            case INTENT_ACTION_CONNECT_BLUETOOTH -> connectBluetooth(intent);
            case INTENT_ACTION_START_MEDIA_PROJECTION -> startMediaProjection(intent);
            case INTENT_ACTION_STOP_CONNECTION -> stopConnection();
            case null -> Log.e(TAG, "no intent action");
            default -> Log.wtf(TAG, "intent action not handled: " + intent.getAction());
        }

        return START_NOT_STICKY;
    }

    private void stopConnectionLocked() {
        Log.i(TAG, "stopping headunit connection");
        if (connector != null) connector.stop();
        if (projectionService != null) {
            projectionService.destroy();
            projectionService = null;
        }

        stopSelf();
    }

    private void stopConnection() {
        synchronized (lock) {
            stopConnectionLocked();
        }
    }

    private final MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            synchronized (lock) {
                Log.i(TAG, "media projection stopped");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    notificationService.removeForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                mediaProjection = null;
            }
        }
    };

    private void startMediaProjection(Intent intent) {
        ActivityResult startResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startResult = intent.getParcelableExtra(INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT, ActivityResult.class);
        } else {
            if (intent.getExtras() == null) {
                Log.wtf(TAG, "intent has no extras");
                return;
            }
            startResult = (ActivityResult) intent.getExtras().get(INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT);
        }

        if (startResult == null || startResult.getResultCode() != Activity.RESULT_OK || startResult.getData() == null) {
            Log.wtf(TAG, "media projection intent without successful media projection permission result: " + startResult);
            return;
        }

        if (mediaProjection != null)
            mediaProjection.stop();

        synchronized (lock) {
            Log.i(TAG, "starting media projection");

            // need this context to start the media projection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

            MediaProjectionManager mediaProjectionManager = getSystemService(MediaProjectionManager.class);
            mediaProjection = mediaProjectionManager.getMediaProjection(startResult.getResultCode(), startResult.getData());
            if (mediaProjection == null) {
                // this probably won't happen
                Log.wtf(TAG, "failed to start media projection");
                return;
            }

            mediaProjection.registerCallback(mediaProjectionCallback, handler);

            if (mediaProjectionRequestCallback != null) {
                mediaProjectionRequestCallback.onAccepted(mediaProjection);
                mediaProjectionRequestCallback = null;
            }
        }
    }

    @Override
    public void onCarNameDiscovered(String carName) {
        notificationService.setCarName(carName);
    }

    private boolean checkKeyguard() {
        if (!settingsManager.allowsStartProjectionWhenLocked() && projectionService == null) {
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km.isKeyguardLocked()) {
                // this is currently default behavior since the keyguard modal in the projection activity is insecure
                Log.e(TAG, "blocking connection due to keyguard");
                notificationService.postError(R.string.unlock_your_phone, R.string.unlock_device_before_connecting);
                return true;
            }
        }
        return false;
    }

    private void connectUsb() {
        synchronized (lock) {
            if (connector != null) {
                Log.e(TAG, "connection already active");
                return;
            }

            if (checkKeyguard()) return;

            projectionGracePeriodToken = new Object();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            Log.i(TAG, "trying to connect over USB");
            connector = new AAUsbConnector(this, this, this, binder, settingsManager);
            connector.start();
        }
    }

    private void startTcpServer() {
        synchronized (lock) {
            if (connector != null) {
                Log.e(TAG, "connection already active");
                return;
            }

            if (checkKeyguard()) return;

            projectionGracePeriodToken = new Object();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            Log.i(TAG, "starting TCP development server");
            connector = new AATcpConnector(this, this, this, binder, settingsManager);
            connector.start();
        }
    }

    private void startWireless(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.wtf(TAG, "intent has no extras");
            return;
        }

        WifiStartRequest connectionInfo = (WifiStartRequest) extras.getSerializable(INTENT_EXTRA_WIRELESS_CONNECTION_INFO);
        WifiInfoResponse wifiInfo = (WifiInfoResponse) extras.getSerializable(INTENT_EXTRA_WIRELESS_WIFI_INFO);

        synchronized (lock) {
            if (connector != null) {
                Log.e(TAG, "connection already active");
                return;
            }

            if (checkKeyguard()) return;

            projectionGracePeriodToken = new Object();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            Log.i(TAG, "starting wireless connection");
            connector = new AAWirelessConnector(this, this, this, binder, settingsManager, connectionInfo, wifiInfo);
            connector.start();
        }

    }

    private void connectBluetooth(Intent intent) {
        String deviceAddress = intent.getStringExtra(INTENT_EXTRA_BLUETOOTH_DEVICE_ADDRESS);
        if (deviceAddress == null) {
            Log.wtf(TAG, "missing required intent extra");
            return;
        }

        synchronized (lock) {
            if (bluetoothClient != null) {
                Log.e(TAG, "bluetooth connection already active");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "missing bluetooth permission");
                    notificationService.postError(new BluetoothConnectionException(this, R.string.bluetooth_connection_error_missing_permission));
                    return;
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "missing location permission (for bluetooth)");
                    notificationService.postError(new BluetoothConnectionException(this, R.string.bluetooth_connection_error_missing_permission));
                    return;
                }
            }

            bluetoothClient = new BluetoothClient(this, deviceAddress, this);

            Log.i(TAG, "connecting to bluetooth");
            try {
                bluetoothClient.connect();
            } catch (BluetoothConnectionException e) {
                Log.e(TAG, "failed to start bluetooth connection", e);
            }
        }
    }


    // projection callbacks
    @Override
    public void onConnectingStatus(@StringRes int status) {
        notificationService.setConnectionStatusText(status);
    }

    @Override
    public void onConnected() {
        notificationService.setConnectionStatusText(R.string.connected_to_car);
        notificationService.clearError();
    }

    @Override
    public void onConnectionError(UserFriendlyException e) {
        notificationService.postError(e);
    }

    @Override
    public void onDisconnected() {
        notificationService.setConnectionStatusText(R.string.disconnected_from_car);
    }

    @Override
    public void onConnectorDeath() {
        synchronized (lock) {
            notificationService.setConnectionStatusText(R.string.looking_for_car);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.removeForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            connector = null;
            suspendProjectionLocked();
        }
    }


    // bluetooth callbacks
    @Override
    public void onStartWireless(WifiStartRequest connectionInfo, WifiInfoResponse wifiInfo) {
        Log.i(TAG, "got request to start wireless");
        startService(new Intent(this, ConnectionService.class)
                .setAction(ConnectionService.INTENT_ACTION_START_WIRELESS)
                .putExtra(ConnectionService.INTENT_EXTRA_WIRELESS_CONNECTION_INFO, connectionInfo)
                .putExtra(ConnectionService.INTENT_EXTRA_WIRELESS_WIFI_INFO, wifiInfo));
    }

    @Override
    public void onBluetoothConnectionError(Throwable t) {
        Log.e(TAG, "bluetooth connection error", t);
        notificationService.postError(new BluetoothConnectionException(this, R.string.bluetooth_connection_error_general_failure, t));
    }

    @Override
    public void onBluetoothDisconnected() {
        Log.v(TAG, "bluetooth disconnected");
        bluetoothClient = null;
    }


    private void suspendProjectionLocked() {
        if (projectionService == null) return;
        if (projectionService.getError() != null) {
            Log.w(TAG, "projection service terminated due to error");
            stopConnectionLocked();
            return;
        }

        long delay = settingsManager.getProjectionResumeGracePeriod() * 1000L;
        if (delay <= 0) {
            Log.i(TAG, "stopping projection due to user preference");
            stopConnectionLocked();
            return;
        }

        projectionService.suspend();

        Object token = projectionGracePeriodToken;
        boolean posted = handler.postDelayed(() -> {
            synchronized (lock) {
                if (token != projectionGracePeriodToken) return;
                Log.i(TAG, "projection service grace period expired");
                stopConnectionLocked();
            }
        }, delay);

        if (!posted) {
            Log.wtf(TAG, "failed to post for projection service grace period, killing immediately");
            stopConnectionLocked();
        }
    }

    public interface MediaProjectionRequestCallback {

        void onAccepted(MediaProjection mediaProjection);

    }

    public class ServiceBinder extends Binder {

        public void requestMediaProjection(MediaProjectionRequestCallback callback) {
            synchronized (lock) {
                if (mediaProjection != null) {
                    callback.onAccepted(mediaProjection);
                    return;
                }

                Log.i(TAG, "requesting to start media projection");
                mediaProjectionRequestCallback = callback;
                startActivity(new Intent(ConnectionService.this, ConnectionRequestActivity.class)
                        .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_MEDIA_PROJECTION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }

        public ProjectionService getOrCreateGeargrinderProjectionService(ProjectionService.Listener listener) {
            synchronized (lock) {
                if (projectionService != null) {
                    if (projectionService.getError() == null) {
                        Log.i(TAG, "resuming existing projection service");
                        projectionService.unsuspend(listener);
                        return projectionService;
                    }

                    Log.i(TAG, "creating new projection service due to error");
                    projectionService.destroy();
                }

                projectionService = new ProjectionService(ConnectionService.this, listener);
                return projectionService;
            }
        }

    }

}
