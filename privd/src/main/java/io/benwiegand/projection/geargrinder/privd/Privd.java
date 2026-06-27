package io.benwiegand.projection.geargrinder.privd;

import static io.benwiegand.projection.geargrinder.privd.reflection.ReflectionUtils.createPrivilegedDisplayManager;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.APP_PKG_NAME;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.BIND_TIMEOUT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.PING_TIMEOUT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.VIRTUAL_ACTIVITY_LAUNCHER_ACTIVITY_COMPONENT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.VIRTUAL_ACTIVITY_LAUNCHER_INTENT_EXTRA_ACTIVITY;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.media.AudioFormat;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.benwiegand.projection.geargrinder.privd.audio.PrivilegedAudioRecordCapture;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedIActivityManager;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedInputEvent;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedInputManager;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;
import io.benwiegand.projection.libprivd.IPrivd;
import io.benwiegand.projection.libprivd.audio.AudioCapture;
import io.benwiegand.projection.libprivd.audio.AudioCaptureResult;

public class Privd extends IPrivd.Stub {
    private static final String TAG = Privd.class.getSimpleName();
    private static final boolean LOG_DEBUG = false;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, VirtualDisplay> virtualDisplays = new ConcurrentHashMap<>();
    private final Map<Integer, AudioCapture> audioCaptures = new ConcurrentHashMap<>();
    private final AtomicInteger audioCaptureIdCounter = new AtomicInteger(0);

    private final int appUid;
    private final DisplayManager dm;
    private final ReflectedInputManager rim;
    private final ReflectedIActivityManager ram;

    private long lastPingAt = 0;

    @SuppressLint("NotificationPermission")
    public Privd(Context context, int appUid) {
        this.context = context;
        this.appUid = appUid;

        DisplayManager dm;
        try {
            dm = createPrivilegedDisplayManager(context);
        } catch (ReflectionException e) {
            Log.w(TAG, "failed to get privileged display manager instance, falling back", e);

            // this is fine for root, but shell will fail
            if (appUid != 0) Log.e(TAG, "virtual displays may fail to create");
            dm = context.getSystemService(DisplayManager.class);
        }

        this.dm = dm;

        InputManager im = context.getSystemService(InputManager.class);
        rim = new ReflectedInputManager(im);

        ReflectedIActivityManager ram;
        try {
            ram = new ReflectedIActivityManager();
        } catch (ReflectionException e) {
            Log.e(TAG, "failed to initialize reflected ActivityManagerNative", e);
            ram = null;
        }

        this.ram = ram;

        // init timeout
        handler.postDelayed(() -> {
            if (lastPingAt != 0) return;
            Log.e(TAG, "timed out waiting for app to bind");
            System.exit(1);
        }, BIND_TIMEOUT);


    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        int callingUid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Binder.getCallingUidOrThrow() : Binder.getCallingUid();
        if (callingUid != appUid)
            throw new SecurityException("only for use by " + APP_PKG_NAME);

        Binder.clearCallingIdentity();

        return super.onTransact(code, data, reply, flags);
    }

    @Override
    public void ping() {
        if (LOG_DEBUG) Log.d(TAG, "ping");
        long pingAt = SystemClock.elapsedRealtime();
        lastPingAt = pingAt;

        // not the most efficient way to do this but it works
        handler.postDelayed(() -> {
            if (lastPingAt > pingAt) return;
            Log.w(TAG, "ping timeout reached");
            System.exit(0);
        }, PING_TIMEOUT);
    }

    @Override
    public boolean injectInputEvent(InputEvent event) {
        if (LOG_DEBUG) Log.d(TAG, "injecting input event: " + event);
        try {
            return rim.injectInputEvent(event, ReflectedInputManager.INJECT_MODE_ASYNC);
        } catch (ReflectionException e) {
            Log.e(TAG, "reflection exception while injecting input event", e);
            return false;
        }
    }

    @Override
    public boolean injectInputEventWithDisplayId(InputEvent event, int displayId) {
        try {
            if (LOG_DEBUG) Log.d(TAG, "setting display id to " + displayId + " for input event: " + event);
            ReflectedInputEvent rEvent = new ReflectedInputEvent(event);
            rEvent.setDisplayId(displayId);
            return injectInputEvent(event);
        } catch (ReflectionException e) {
            Log.e(TAG, "failed to set display id", e);
            return false;
        }
    }

    @Override
    public int launchActivity(ComponentName component, int displayId) {
        Log.v(TAG, "launching activity on display " + displayId + ": " + component.flattenToShortString());

        if (ram != null) {
            try {
                Intent intent = new Intent()
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .setComponent(component);

                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(displayId);

                return ram.startActivity(intent, opts.toBundle());
            } catch (ReflectionException | SecurityException e) {
                Log.w(TAG, "failed to launch activity via IActivityManager", e);
            }
        }

        Log.w(TAG, "falling back to shell command for activity launch");
        try {
            return new ProcessBuilder("am", "start-activity", "--display", String.valueOf(displayId), component.flattenToShortString())
                    .start()
                    .waitFor();
        } catch (IOException e) {
            Log.e(TAG, "IOException while starting activity via shell", e);
            return -1;
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            return -1;
        }
    }

    @Override
    public int launchVirtualActivity(ComponentName component, int displayId) {
        Log.v(TAG, "launching virtual activity launcher on display " + displayId + ": " + component.flattenToShortString());

        if (ram != null) {
            try {
                Intent intent = new Intent()
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        .putExtra(VIRTUAL_ACTIVITY_LAUNCHER_INTENT_EXTRA_ACTIVITY, component.flattenToString())
                        .setComponent(VIRTUAL_ACTIVITY_LAUNCHER_ACTIVITY_COMPONENT);

                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(displayId);

                return ram.startActivity(intent, opts.toBundle());
            } catch (ReflectionException | SecurityException e) {
                Log.w(TAG, "failed to launch activity via IActivityManager", e);
            }
        }

        Log.w(TAG, "falling back to shell command for activity launch");
        try {
            return new ProcessBuilder("am", "start-activity",
                    "--activity-multiple-task",
                    "--es", VIRTUAL_ACTIVITY_LAUNCHER_INTENT_EXTRA_ACTIVITY, component.flattenToString(),
                    "--display", String.valueOf(displayId),
                    VIRTUAL_ACTIVITY_LAUNCHER_ACTIVITY_COMPONENT.flattenToShortString())
                    .start()
                    .waitFor();
        } catch (IOException e) {
            Log.e(TAG, "IOException while starting activity via shell", e);
            return -1;
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            return -1;
        }
    }

    private VirtualDisplay getVirtualDisplay(int displayId) {
        VirtualDisplay virtualDisplay = virtualDisplays.get(displayId);
        if (virtualDisplay == null) throw new NoSuchElementException("no registered VirtualDisplay exists with id " + displayId);
        return virtualDisplay;
    }

    private AudioCapture getAudioCapture(int id) {
        AudioCapture audioCapture = audioCaptures.get(id);
        if (audioCapture == null) throw new NoSuchElementException("no AudioCapture exists with id " + id);
        return audioCapture;
    }

    @Override
    public int createVirtualDisplay(String name, int width, int height, int densityDpi, Surface surface, int flags) {
        Log.v(TAG, "creating virtual display: " + name);

        VirtualDisplay virtualDisplay = dm.createVirtualDisplay(name, width, height, densityDpi, surface, flags);
        if (virtualDisplay == null) {
            Log.e(TAG, "createVirtualDisplay() returned null", new RuntimeException());
            return -1;
        }

        int displayId = virtualDisplay.getDisplay().getDisplayId();
        Log.v(TAG, "created virtual display " + displayId + " with name: " + name);

        virtualDisplays.put(displayId, virtualDisplay);
        return displayId;
    }

    @Override
    public void releaseVirtualDisplay(int displayId) {
        Log.v(TAG, "releasing virtual display " + displayId);
        VirtualDisplay virtualDisplay = virtualDisplays.remove(displayId);
        if (virtualDisplay == null) {
            Log.w(TAG, "can't release virtual display " + displayId + " because it doesn't exist or was already released");
            return;
        }

        virtualDisplay.release();
    }

    @Override
    public void virtualDisplayResize(int displayId, int width, int height, int densityDpi) {
        Log.v(TAG, "resizing virtual display " + displayId + " to " + width + " x " + height + " with " + densityDpi + " dpi");

        VirtualDisplay virtualDisplay = getVirtualDisplay(displayId);
        virtualDisplay.resize(width, height, densityDpi);
    }

    @Override
    public void virtualDisplaySetSurface(int displayId, Surface surface) {
        Log.v(TAG, "setting new output surface for virtual display " + displayId);

        VirtualDisplay virtualDisplay = getVirtualDisplay(displayId);
        virtualDisplay.setSurface(surface);
    }


    @Override
    public int createPrivilegedAudioRecordCapture(AudioFormat audioFormat, int bufferSize, int audioSource) throws RemoteException {
        int id = audioCaptureIdCounter.getAndIncrement();
        Log.v(TAG, "creating privileged audio record capture: " + id);

        try {
            // TODO: need foreground context on A11

            PrivilegedAudioRecordCapture audioCapture = new PrivilegedAudioRecordCapture(context, audioFormat, bufferSize, audioSource);
            audioCaptures.put(id, audioCapture);
        } catch (Throwable t) {
            Log.e(TAG, "failed to create privileged audio record capture", t);
            return -1;
        }

        return id;
    }

    @Override
    public void destroyAudioCapture(int id) {
        Log.v(TAG, "destroying audio capture" + id);
        AudioCapture audioCapture = audioCaptures.remove(id);
        if (audioCapture == null) {
            Log.w(TAG, "can't destroy audio capture " + id + " because it doesn't exist or was already destroyed");
            return;
        }

        audioCapture.destroy();
    }

    @Override
    public void audioCaptureBegin(int id) {
        Log.v(TAG, "audio capture begin: " + id);
        getAudioCapture(id).begin();
    }

    @Override
    public void audioCaptureNextBuffer(int id, AudioCaptureResult result, byte[] buffer, int offset, int length) {
        if (LOG_DEBUG) Log.d(TAG, "getting next buffer for audio capture: " + id);
        getAudioCapture(id).nextBuffer(result, buffer, offset, length);
    }
}
