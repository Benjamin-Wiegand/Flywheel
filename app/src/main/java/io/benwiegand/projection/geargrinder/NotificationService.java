package io.benwiegand.projection.geargrinder;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.callback.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;

public class NotificationService extends NotificationListenerService implements MakeshiftBindCallback {
    private static final String TAG = NotificationService.class.getSimpleName();

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;

    private final Queue<NotificationListener> listeners = new LinkedList<>();

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


    public class ServiceBinder extends Binder {

        public void registerListener(NotificationListener listener) {
            listeners.add(listener);
        }

        public void unregisterCallback(NotificationListener listener) {
            listeners.remove(listener);
        }

    }


}
