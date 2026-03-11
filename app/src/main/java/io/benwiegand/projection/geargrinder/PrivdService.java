package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.BIND_TIMEOUT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_ACTION_BIND_PRIVD;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_EXTRA_BINDER;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.PING_INTERVAL;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.settings.PrivilegeMode;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.privileged.PrivdLauncher;
import io.benwiegand.projection.geargrinder.privileged.ShizukuPrivdLauncher;
import io.benwiegand.projection.libprivd.IPrivd;

public class PrivdService extends Service {
    private static final String TAG = PrivdService.class.getSimpleName();

    private final Object lock = new Object();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private final List<IPCConnectionListener> ipcConnectionListeners = new LinkedList<>();

    private PrivdLauncher privdLauncher = null;
    private IPrivd privd = null;
    private boolean launchInProgress = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        tryLaunchPrivd();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        synchronized (lock) {
            privd = null;
            if (privdLauncher != null)
                privdLauncher.destroy();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void onBindPrivd(Bundle bundle) {
        if (bundle == null) {
            Log.wtf(TAG, "bundle is null");
            return;
        }

        IBinder binder = bundle.getBinder(INTENT_EXTRA_BINDER);
        if (binder == null) {
            Log.wtf(TAG, "binder is null");
            return;
        }

        if (!binder.isBinderAlive()) {
            Log.wtf(TAG, "binder is dead");
            return;
        }

        synchronized (lock) {
            if (!launchInProgress) {
                Log.wtf(TAG, "got bind intent from daemon but no launch in progress");
                return;
            }

            Log.i(TAG, "IPC connected");
            launchInProgress = false;
            privd = IPrivd.Stub.asInterface(binder);
            callListenersLocked(l -> l.onPrivdConnected(privd));
        }

        pingBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + intent);

        if (INTENT_ACTION_BIND_PRIVD.equals(intent.getAction()))
            onBindPrivd(intent.getExtras());

        return START_NOT_STICKY;
    }

    private boolean isPrivdConnected() {
        return privd != null && privd.asBinder().isBinderAlive();
    }

    private void tryLaunchPrivdLocked() {
        if (isPrivdConnected()) return;
        if (launchInProgress) return;

        try {
            if (privdLauncher == null) {
                SettingsManager settings = new SettingsManager(this);

                PrivilegeMode privilegeMode = settings.getPrivilegeMode();
                Log.d(TAG, "getting launcher for privilege mode: " + privilegeMode);
                privdLauncher = PrivdLauncher.createForPrivilegeMode(privilegeMode, this);
                if (privdLauncher == null)
                    throw new IllegalStateException("privd not needed for current privilege mode");
            }

            if (privdLauncher instanceof ShizukuPrivdLauncher shizukuPrivdLauncher) shizukuPrivdLauncher
                    .checkShizukuPermission()
                    .filter(r -> r)
                    .ifPresentOrElse(
                            r -> Log.i(TAG, "shizuku connected and permission granted"),
                            () -> {
                                Log.e(TAG, "shizuku not connected or permission denied");
                                startActivity(new Intent(this, ConnectionRequestActivity.class)
                                        .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_SHIZUKU)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            }
                    );

            privdLauncher.setErrorListener(t -> {
                synchronized (lock) {
                    if (isPrivdConnected() || !launchInProgress) return;
                    launchInProgress = false;
                    onPrivdLaunchFailure(t);
                }
            });

            privdLauncher.launch();
            handler.postDelayed(() -> {
                synchronized (lock) {
                    if (isPrivdConnected() || !launchInProgress) return;
                    Log.e(TAG, "timed out waiting for privd to launch");
                    launchInProgress = false;
                    onPrivdLaunchFailure(new TimeoutException("timed out waiting for binder"));
                }
            }, BIND_TIMEOUT);

            launchInProgress = true;
        } catch (Throwable t) {
            launchInProgress = false;
            onPrivdLaunchFailure(t);
        }
    }

    private void tryLaunchPrivd() {
        synchronized (lock) {
            tryLaunchPrivdLocked();
        }
    }

    private void pingBinder() {
        synchronized (lock) {
            if (privd == null) return;

            try {
                privd.ping();
            } catch (Throwable t) {
                Log.e(TAG, "failed to ping binder", t);
                onPrivdDisconnected();
                return;
            }

            handler.postDelayed(this::pingBinder, PING_INTERVAL);
        }
    }

    private void callListenersLocked(Consumer<IPCConnectionListener> consumer) {
        for (IPCConnectionListener listener : ipcConnectionListeners) {
            try {
                consumer.accept(listener);
            } catch (Throwable t) {
                Log.wtf(TAG, "exception in ipc connection listener", t);
            }
        }
    }

    private void onPrivdDisconnected() {
        synchronized (lock) {
            Log.i(TAG, "IPC disconnected");
            privd = null;

            callListenersLocked(IPCConnectionListener::onPrivdDisconnected);
            ipcConnectionListeners.clear();
            stopSelf();
        }
    }

    private void onPrivdLaunchFailure(Throwable t) {
        synchronized (lock) {
            Log.e(TAG, "failed to launch daemon", t);
            privd = null;

            callListenersLocked(l -> l.onPrivdLaunchFailure(t));
            ipcConnectionListeners.clear();
            stopSelf();
        }
    }

    public class ServiceBinder extends Binder {

        public void requestDaemon(IPCConnectionListener listener) {
            synchronized (lock) {
                ipcConnectionListeners.add(listener);

                if (isPrivdConnected()) {
                    listener.onPrivdConnected(privd);
                } else {
                    tryLaunchPrivdLocked();
                }
            }
        }

    }
}
