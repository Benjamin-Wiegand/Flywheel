package io.benwiegand.projection.geargrinder;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.privileged.PrivdIPCConnection;
import io.benwiegand.projection.geargrinder.privileged.RootPrivdLauncher;

public class PrivdService extends Service implements IPCConnectionListener {
    private static final String TAG = PrivdService.class.getSimpleName();
    private static final int PRIVD_LAUNCH_TIMEOUT = 10000;

    private final Object lock = new Object();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private RootPrivdLauncher privdLauncher;
    private PrivdIPCConnection connection = null;

    private final List<IPCConnectionListener> ipcConnectionListeners = new LinkedList<>();

    private boolean dead = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        privdLauncher = new RootPrivdLauncher(this, this);

        synchronized (lock) {
            try {
                privdLauncher.launchRoot();
                handler.postDelayed(() -> {
                    synchronized (lock) {
                        if (connection != null && connection.isAlive()) return;
                        if (dead) return;

                        Log.e(TAG, "timed out waiting for privd to launch");
                        dieLocked();
                    }
                }, PRIVD_LAUNCH_TIMEOUT);
            } catch (IOException e) {
                Log.e(TAG, "failed to launch daemon", e);
                dead = true;
                stopSelf();
            }
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        privdLauncher.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    private void dieLocked() {
        dead = true;

        for (IPCConnectionListener listener : ipcConnectionListeners)
            listener.onPrivdDisconnected();

        ipcConnectionListeners.clear();
        stopSelf();
    }

    @Override
    public void onPrivdConnected(PrivdIPCConnection connection) {
        synchronized (lock) {
            Log.i(TAG, "IPC connected");
            this.connection = connection;

            for (IPCConnectionListener listener : ipcConnectionListeners)
                listener.onPrivdConnected(connection);
        }
    }

    @Override
    public void onPrivdDisconnected() {
        synchronized (lock) {
            Log.i(TAG, "IPC disconnected");
            connection = null;
            dieLocked();
        }
    }

    public class ServiceBinder extends Binder {

        public void addDaemonListener(IPCConnectionListener listener) {
            synchronized (lock) {
                if (dead) {
                    listener.onPrivdDisconnected();
                    return;
                }

                if (connection != null && connection.isAlive())
                    listener.onPrivdConnected(connection);

                ipcConnectionListeners.add(listener);
            }
        }

    }
}
