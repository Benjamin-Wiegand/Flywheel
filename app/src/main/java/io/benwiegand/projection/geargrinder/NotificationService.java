package io.benwiegand.projection.geargrinder;

import android.content.ComponentName;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.callback.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;

public class NotificationService extends NotificationListenerService implements MakeshiftBindCallback {
    private static final String TAG = NotificationService.class.getSimpleName();

    private static final long MEDIA_INTERRUPTION_END_DELAY = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;

    private final Queue<NotificationListener> listeners = new LinkedList<>();

    private final Set<String> activeMediaInterruptions = new HashSet<>();
    private MediaController interruptedMediaSessionController = null;
    private Object interruptionToken = new Object();

    public interface NotificationListener {
        void onNotificationPosted(StatusBarNotification sbn);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "notification listener created");

        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, NotificationService.class), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "notification listener destroyed");
        makeshiftBind.destroy();
    }

    @Override
    public IBinder onMakeshiftBind(Intent intent) {
        // overriding onBind() breaks everything, don't ask me how I know
        return binder;
    }

    private void callListeners(Consumer<NotificationListener> consumer) {
        for (NotificationListener listener : listeners) {
            try {
                consumer.accept(listener);
            } catch (Throwable t) {
                Log.e(TAG, "exception thrown while calling listener", t);
                assert false;
            }
        }
    }

    @Override
    public void onListenerConnected() {
        Log.v(TAG, "notification listener connected");
    }

    @Override
    public void onListenerDisconnected() {
        Log.v(TAG, "notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "notification posted");
        callListeners(l -> l.onNotificationPosted(sbn));
    }

    private MediaController findActiveMediaSessionController() {
        MediaSessionManager mediaSessionManager = getSystemService(MediaSessionManager.class);
        List<MediaController> sessions = mediaSessionManager.getActiveSessions(new ComponentName(this, NotificationService.class));

        for (MediaController controller : sessions) {
            PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState == null) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!playbackState.isActive()) continue;
            } else if (playbackState.getState() == PlaybackState.STATE_PLAYING) {
                continue;
            }

            return controller;
        }

        return null;
    }

    private void beginMediaInterruption(String interruptionId) {
        synchronized (activeMediaInterruptions) {
            Log.d(TAG, "adding interruption: " + interruptionId);
            boolean interruptionStart = activeMediaInterruptions.isEmpty();
            activeMediaInterruptions.add(interruptionId);
            if (!interruptionStart) return;

            Log.i(TAG, "starting media interruption");

            interruptionToken = new Object();
            if (interruptedMediaSessionController != null) {
                Log.d(TAG, "continuing previous interruption");
                return;
            }

            interruptedMediaSessionController = findActiveMediaSessionController();
            if (interruptedMediaSessionController == null) {
                Log.d(TAG, "no active media session");
                return;
            }

            try {
                interruptedMediaSessionController.getTransportControls().pause();
            } catch (Throwable t) {
                Log.e(TAG, "failed to pause media", t);
            }
        }
    }

    private void endMediaInterruption(String interruptionId) {
        synchronized (activeMediaInterruptions) {
            Log.d(TAG, "removing interruption: " + interruptionId);
            if (!activeMediaInterruptions.remove(interruptionId)) {
                Log.e(TAG, "no such interruption: " + interruptionId);
                return;
            }
            if (!activeMediaInterruptions.isEmpty()) return;

            Log.i(TAG, "scheduling end of media interruption");
            Object token = interruptionToken;

            handler.postDelayed(() -> {
                synchronized (activeMediaInterruptions) {
                    if (!activeMediaInterruptions.isEmpty()) return;
                    if (token != interruptionToken) return;

                    Log.i(TAG, "stopping media interruption");
                    if (interruptedMediaSessionController == null) {
                        Log.d(TAG, "nothing to resume");
                        return;
                    }

                    try {
                        interruptedMediaSessionController.getTransportControls().play();
                    } catch (Throwable t) {
                        Log.e(TAG, "failed to resume media", t);
                    }

                    interruptedMediaSessionController = null;
                }
            }, MEDIA_INTERRUPTION_END_DELAY);
        }
    }


    public class ServiceBinder extends Binder {

        public void registerListener(NotificationListener listener) {
            listeners.add(listener);
        }

        public void unregisterCallback(NotificationListener listener) {
            listeners.remove(listener);
        }

        public void beginMediaInterruption(String interruptionId) {
            NotificationService.this.beginMediaInterruption(interruptionId);
        }

        public void endMediaInterruption(String interruptionId) {
            NotificationService.this.endMediaInterruption(interruptionId);
        }

    }


}
