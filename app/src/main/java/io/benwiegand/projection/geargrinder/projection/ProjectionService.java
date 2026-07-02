package io.benwiegand.projection.geargrinder.projection;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;

import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.ProjectionActivity;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.channel.InputChannel;
import io.benwiegand.projection.geargrinder.exception.ProjectionException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.projection.input.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.projection.input.InputEventConverter;
import io.benwiegand.projection.geargrinder.projection.display.LocalVirtualDisplayController;
import io.benwiegand.projection.geargrinder.projection.display.PrivdVirtualDisplayProxy;
import io.benwiegand.projection.geargrinder.projection.display.VirtualDisplayController;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionService implements InputEventConverter.ConvertedInputEventListener, IPCConnectionListener, GeargrinderServiceConnector.ConnectionListener {
    private static final String TAG = ProjectionService.class.getSimpleName();

    private static final String VIRTUAL_DISPLAY_NAME = "Geargrinder projection";

    // privd can use system/protected flags
    private static final int PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | PrivdVirtualDisplayProxy.FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD
            | PrivdVirtualDisplayProxy.FLAG_SUPPORTS_TOUCH
            | PrivdVirtualDisplayProxy.FLAG_TRUSTED
            | PrivdVirtualDisplayProxy.FLAG_OWN_DISPLAY_GROUP
            | PrivdVirtualDisplayProxy.FLAG_ALWAYS_UNLOCKED
            | PrivdVirtualDisplayProxy.FLAG_OWN_FOCUS;

    // sets of flags to try since sometimes not all of them work
    private static final int[] PRIVD_VIRTUAL_DISPLAY_FLAGS = new int[] {
            PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE,
            PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS,
    };

    // this is all that really can be done
    private static final int LOCAL_VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

    private final Object lock = new Object();

    private InputEventConverter inputEventConverter;

    private VirtualDisplayController virtualDisplay = null;
    private VideoPreset videoPreset;
    private Surface surface = null;

    private final GeargrinderServiceConnector connector;
    private IPrivd privd = null;

    private final Context context;
    private final AudioManager audioManager;

    public interface Listener {
        void onProjectionStarted();
        void onProjectionFailed(UserFriendlyException e);
    }

    private Listener projectionListener;
    private boolean started = false;
    private boolean inputInit = false;
    private boolean outputInit = false;
    private boolean uiInit = false;
    private boolean virtualDisplayInit = false;

    private boolean dead = false;
    private UserFriendlyException error = null;


    public ProjectionService(Context context, Listener projectionListener, VideoPreset videoPreset) {
        this.context = context;
        this.projectionListener = projectionListener;
        this.videoPreset = videoPreset;
        audioManager = context.getSystemService(AudioManager.class);

        CoordinateTranslator<TouchEvent.PointerLocation> coordinateTranslator = CoordinateTranslator.createTouchEvent(
                x -> x + this.videoPreset.marginHorizontal() / 2,
                y -> y + this.videoPreset.marginVertical() / 2
        );
        inputEventConverter = new InputEventConverter(InputChannelMeta.getDefault(), this, coordinateTranslator, 0, videoPreset.width(), videoPreset.height());

        connector = new GeargrinderServiceConnector(TAG, context, this);
        connector.bindAccessibilityService();
        connector.bindProjectionActivity();
        connector.bindPrivdService(BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    public ProjectionService(Context context, Listener projectionListener) {
        this(context, projectionListener, VideoPreset.getDefault());
    }

    public void destroy() {
        if (dead) return;
        dead = true;
        connector.destroy();
        if (virtualDisplay != null)
            virtualDisplay.release();

        if (started) pauseMedia();  // already paused if projection is suspended
    }

    public Throwable getError() {
        return error;
    }

    private void onInitAdvancedLocked() {
        if (started) return;
        if (!inputInit || !outputInit || !uiInit || !virtualDisplayInit) return;
        started = true;

        Log.i(TAG, "init complete");
        projectionListener.onProjectionStarted();
    }

    private void onFailureLocked(UserFriendlyException e) {
        if (dead) return;
        e.fillInStackTrace();
        if (error != null) {
            Log.w(TAG, "projection already failed, but another failure happened", e);
            return;
        }
        Log.e(TAG, "projection failure: ", e);
        error = e;
        projectionListener.onProjectionFailed(error);
    }

    private void pauseMedia() {
        Log.i(TAG, "pausing media");
        audioManager.requestAudioFocus(new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .build());
    }

    public void unsuspend(Listener projectionListener) {
        synchronized (lock) {
            Log.i(TAG, "unsuspending projection");
            assert !dead;
            if (started) Log.w(TAG, "unsuspend() called but projection already unsuspended");

            this.projectionListener = projectionListener;
            if (error != null) projectionListener.onProjectionFailed(error);
            else if (started) projectionListener.onProjectionStarted();
        }
    }

    public void suspend() {
        synchronized (lock) {
            Log.i(TAG, "suspending projection");
            // ui and virtual display stay active so the projection can be resumed
            started = false;
            inputInit = false;
            outputInit = false;

            if (virtualDisplay != null) virtualDisplay.setSurface(null);

            pauseMedia();
        }
    }

    public void setOutput(Surface surface, VideoPreset videoPreset) {
        synchronized (lock) {
            Log.i(TAG, "attaching new output");
            if (!this.videoPreset.equals(videoPreset)) {
                Log.d(TAG, "resizing to " + videoPreset.width() + " x " + videoPreset.height() + ", dpi = " + videoPreset.density());

                if (virtualDisplay != null)
                    virtualDisplay.resize(videoPreset.width(), videoPreset.height(), videoPreset.density());

                inputEventConverter.setTargetDisplaySize(videoPreset.width(), videoPreset.height());
                connector.getProjectionBinder()
                        .ifPresent(binder -> binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical()));

                this.videoPreset = videoPreset;
            }

            if (virtualDisplay != null)
                virtualDisplay.setSurface(surface);

            this.surface = surface;

            outputInit = true;
            onInitAdvancedLocked();
        }
    }

    public void setInput(InputChannel inputChannel) {
        synchronized (lock) {
            inputEventConverter.setInputMeta(inputChannel.getMetadata());
            inputChannel.setInputEventListener(inputEventConverter);

            inputInit = true;
            onInitAdvancedLocked();
        }
    }

    @Override
    public void onInputEvent(InputEvent event, int displayId, boolean displayIdSet) {
        if (!started) return;

        try {
            boolean result = displayIdSet ? privd.injectInputEvent(event) : privd.injectInputEventWithDisplayId(event, displayId);
            if (!result) Log.w(TAG, "motion event result is false");
        } catch (Throwable t) {
            Log.e(TAG, "failed to inject motion event", t);
        }
    }

    @Override
    public void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {
        synchronized (lock) {
            binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical());

            uiInit = true;
            onInitAdvancedLocked();
        }
    }

    @Override
    public void onPrivdServiceConnected(PrivdService.ServiceBinder binder) {
        binder.requestDaemon(this);
    }

    @Override
    public void onPrivdConnected(IPrivd privd) {
        Log.i(TAG, "starting projection");
        this.privd = privd;

        synchronized (lock) {
            if (virtualDisplayInit) return;
            assert virtualDisplay == null;

            try {
                Log.d(TAG, "creating virtual display via privd");
                virtualDisplay = PrivdVirtualDisplayProxy.tryCreateWithFallbackFlags(
                        privd, VIRTUAL_DISPLAY_NAME,
                        videoPreset.width(), videoPreset.height(), videoPreset.density(),
                        surface, PRIVD_VIRTUAL_DISPLAY_FLAGS
                );
            } catch (Throwable t) {
                Log.e(TAG, "failed to create virtual display via privd", t);

                try {
                    // this is less consequential for ProjectionService than it is for VirtualActivity
                    Log.w(TAG, "falling back to local virtual display");
                    DisplayManager dm = context.getSystemService(DisplayManager.class);
                    virtualDisplay = new LocalVirtualDisplayController(
                            dm, VIRTUAL_DISPLAY_NAME,
                            videoPreset.width(), videoPreset.height(), videoPreset.density(),
                            surface, LOCAL_VIRTUAL_DISPLAY_FLAGS
                    );
                } catch (Throwable tt) {
                    onFailureLocked(new ProjectionException(context, R.string.projection_error_virtual_display_create_failure, t));
                    return;
                }
            }

            inputEventConverter.setTargetDisplayId(virtualDisplay.getDisplayId());

            try {
                Log.d(TAG, "launching projection activity");
                int ret = privd.launchActivity(
                        new ComponentName(context, ProjectionActivity.class),
                        virtualDisplay.getDisplayId()
                );
                if (ret == -1) throw new RuntimeException("activity launch failed with code -1");
            } catch (Throwable t) {
                onFailureLocked(new ProjectionException(context, R.string.projection_error_activity_launch_failure, t));
                return;
            }

            virtualDisplayInit = true;
            onInitAdvancedLocked();
        }
    }

    @Override
    public void onPrivdDisconnected() {
        synchronized (lock) {
            onFailureLocked(new ProjectionException(context, R.string.projection_error_privd_disconnected));
        }
    }

    @Override
    public void onPrivdLaunchFailure(UserFriendlyException e) {
        synchronized (lock) {
            onFailureLocked(new ProjectionException(context, R.string.projection_error_privd_launch_failure, e));
        }
    }

}
