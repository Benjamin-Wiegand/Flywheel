package io.benwiegand.projection.geargrinder.projection.lock;

import android.app.KeyguardManager;
import android.content.Context;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Queue;

// tracks the keyguard state for the projection
// unlocking the device once is considered sufficient to unlock the projection until it stops
public class ProjectionKeyguardTracker {
    private static final String TAG = ProjectionKeyguardTracker.class.getSimpleName();

    private static final long KEYGUARD_LOCK_STATE_POLL_INTERVAL = 1000;

    private final Object lock = new Object();

    private final Thread checkThread = new Thread(this::checkLoop);
    private final Context context;

    private boolean screenLocked = true;
    private boolean dead = false;

    private final Queue<Runnable> unlockCallbacks = new ArrayDeque<>();

    public ProjectionKeyguardTracker(Context context) {
        this.context = context;
    }

    public void destroy() {
        synchronized (lock) {
            unlockCallbacks.clear();
            dead = true;
            checkThread.interrupt();
        }
    }

    public void start() {
        checkThread.start();
    }

    public void registerUnlockCallback(Runnable callback) {
        synchronized (lock) {
            if (!screenLocked) {
                callback.run();
                return;
            }

            unlockCallbacks.add(callback);
        }
    }

    private void checkLoop() {
        KeyguardManager km = context.getSystemService(KeyguardManager.class);
        if (km.isKeyguardLocked()) Log.i(TAG, "device is locked");

        while (!dead) {
            if (!km.isKeyguardLocked()) {
                Log.i(TAG, "device unlocked");
                synchronized (lock) {
                    screenLocked = false;
                    for (Runnable callback : unlockCallbacks) {
                        try {
                            callback.run();
                        } catch (Throwable t) {
                            Log.wtf(TAG, "exception thrown by callback", t);
                            assert false;
                        }
                    }

                    unlockCallbacks.clear();
                }
                return;
            }

            try {
                // noinspection BusyWait: no choice, the api sucks
                Thread.sleep(KEYGUARD_LOCK_STATE_POLL_INTERVAL);
            } catch (InterruptedException ignored) {}
        }
    }
}
