package io.benwiegand.projection.geargrinder;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.connector.AAConnector;
import io.benwiegand.projection.geargrinder.connector.AATcpConnector;
import io.benwiegand.projection.geargrinder.connector.AAUsbConnector;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.notification.ConnectionNotificationService;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class ConnectionService extends Service implements ControlListener, AAConnector.StateListener {
    private static final String TAG = ConnectionService.class.getSimpleName();

    public static final String INTENT_ACTION_CONNECT_USB = "io.benwiegand.projection.geargrinder.USB_HEADUNIT_CONNECTED";
    public static final String INTENT_ACTION_START_TCP = "io.benwiegand.projection.geargrinder.START_TCP_SERVER";
    public static final String INTENT_ACTION_START_MEDIA_PROJECTION = "io.benwiegand.projection.geargrinder.START_MEDIA_PROJECTION";
    public static final String INTENT_ACTION_STOP_CONNECTION = "io.benwiegand.projection.geargrinder.STOP_CONNECTION";

    public static final String INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT = "projection_result";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private final Object lock = new Object();

    private ConnectionNotificationService notificationService;
    private SettingsManager settingsManager;

    private AAConnector connector = null;
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
        switch (intent.getAction()) {
            case INTENT_ACTION_CONNECT_USB -> connectUsb();
            case INTENT_ACTION_START_TCP -> startTcpServer();
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

    private void connectUsb() {
        synchronized (lock) {
            if (connector != null) {
                Log.e(TAG, "connection already active");
                return;
            }

            if (!settingsManager.allowsStartProjectionWhenLocked() && projectionService == null) {
                KeyguardManager km = getSystemService(KeyguardManager.class);
                if (km.isKeyguardLocked()) {
                    // this is currently default behavior since the keyguard modal in the projection activity is insecure
                    Log.e(TAG, "blocking connection due to keyguard");
                    notificationService.postError(R.string.unlock_your_phone, R.string.unlock_device_before_connecting);
                    return;
                }
            }

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

            projectionGracePeriodToken = new Object();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            Log.i(TAG, "starting TCP development server");
            connector = new AATcpConnector(this, this, this, binder, settingsManager);
            connector.start();
        }
    }

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
