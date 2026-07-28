package io.benwiegand.projection.geargrinder.projection.ui;

import static android.media.session.PlaybackState.*;
import static android.media.session.PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
import static android.media.session.PlaybackState.STATE_STOPPED;

import static io.benwiegand.projection.geargrinder.util.UiUtil.dpToPx;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.NotificationService;
import io.benwiegand.projection.geargrinder.PackageService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTaskManager;
import io.benwiegand.projection.geargrinder.thread.ResourceLoaderThread;

public class MediaControlsWidget {
    private static final String TAG = MediaControlsWidget.class.getSimpleName();

    private final Context context;
    private final View root;
    private final Handler handler;

    private final ProjectionTaskManager taskManager;
    private final Supplier<Optional<PackageService.ServiceBinder>> getPackageBinder;

    private final ResourceLoaderThread artLoaderThread;

    private final ComponentName notificationListenerComponent;
    private final MediaSessionManager mediaSessionManager;

    private MediaController activeSession = null;
    private boolean playing;

    public MediaControlsWidget(View root, Handler handler, ProjectionTaskManager taskManager, Supplier<Optional<PackageService.ServiceBinder>> getPackageBinder) {
        context = root.getContext();
        this.root = root;
        this.handler = handler;
        this.taskManager = taskManager;
        this.getPackageBinder = getPackageBinder;

        artLoaderThread = new ResourceLoaderThread(handler, -1);

        notificationListenerComponent = new ComponentName(context, NotificationService.class);
        mediaSessionManager = context.getSystemService(MediaSessionManager.class);
        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(this::onActiveMediaSessionsChanged, notificationListenerComponent, handler);
            onActiveMediaSessionsChanged(mediaSessionManager.getActiveSessions(notificationListenerComponent));
        } catch (SecurityException e) {
            Log.w(TAG, "missing permission for media controls: " + e);
            hide();
        }

        root.findViewById(R.id.touch_target).setOnClickListener(v -> openNowPlayingApp());
        root.findViewById(R.id.skip_prev_button).setOnClickListener(v -> prevTrack());
        root.findViewById(R.id.play_pause_button).setOnClickListener(v -> playPause());
        root.findViewById(R.id.skip_next_button).setOnClickListener(v -> nextTrack());
    }

    public void destroy() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(this::onActiveMediaSessionsChanged);
    }

    private void hide() {
        root.setVisibility(View.GONE);
    }

    private void show() {
        root.setVisibility(View.VISIBLE);
    }

    private void updatePlaybackState(PlaybackState playbackState) {
        ImageButton skipPrevButton = root.findViewById(R.id.skip_prev_button);
        ImageButton playPauseButton = root.findViewById(R.id.play_pause_button);
        ImageButton skipNextButton = root.findViewById(R.id.skip_next_button);
        View bufferingIndicator = root.findViewById(R.id.buffering_indicator);
        View errorIndicator = root.findViewById(R.id.playback_error_indicator);
        int state = playbackState != null ? playbackState.getState() : STATE_NONE;
        switch (state) {
            case STATE_BUFFERING:
            case STATE_CONNECTING:
            case STATE_SKIPPING_TO_PREVIOUS:
            case STATE_SKIPPING_TO_QUEUE_ITEM:
            case STATE_SKIPPING_TO_NEXT:
                playPauseButton.setImageResource(R.drawable.pause);
                bufferingIndicator.setVisibility(View.VISIBLE);
                errorIndicator.setVisibility(View.GONE);
                playing = true;
                break;
            case STATE_ERROR:
                playPauseButton.setImageResource(R.drawable.play_arrow);
                bufferingIndicator.setVisibility(View.GONE);
                errorIndicator.setVisibility(View.VISIBLE);
                playing = false;
                break;
            case STATE_PAUSED:
            case STATE_STOPPED:
            case STATE_NONE:
                playPauseButton.setImageResource(R.drawable.play_arrow);
                bufferingIndicator.setVisibility(View.GONE);
                errorIndicator.setVisibility(View.GONE);
                playing = false;
                break;
            case STATE_PLAYING:
            case STATE_FAST_FORWARDING:
            case STATE_REWINDING:
                playPauseButton.setImageResource(R.drawable.pause);
                bufferingIndicator.setVisibility(View.GONE);
                errorIndicator.setVisibility(View.GONE);
                playing = true;
                break;
            default:
                Log.wtf(TAG, "unhandled playback state: " + state, new AssertionError());
                playPauseButton.setImageResource(R.drawable.pause);
                bufferingIndicator.setVisibility(View.GONE);
                errorIndicator.setVisibility(View.GONE);
                playing = true;
                break;
        }

        long actions = playbackState != null ? playbackState.getActions() : 0;
        skipPrevButton.setVisibility((actions & ACTION_SKIP_TO_PREVIOUS) != 0 ? View.VISIBLE : View.GONE);
        skipNextButton.setVisibility((actions & ACTION_SKIP_TO_NEXT) != 0 ? View.VISIBLE : View.GONE);
    }

    private void updateMetadata(MediaMetadata metadata) {
        ImageView artImageView = root.findViewById(R.id.media_art_image);
        artImageView.setImageResource(R.drawable.music);
        if (metadata == null) return;

        artLoaderThread.execute(() -> {
            Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bitmap == null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            int imageSize = (int) dpToPx(context, 48);
            return Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true);
        }, bitmap -> {
            if (bitmap == null) return;
            artImageView.setImageBitmap(bitmap);
        });
    }

    private void updateNowPlaying() {
        if (activeSession == null) {
            updatePlaybackState(null);
            updateMetadata(null);
            hide();
            return;
        }
        updatePlaybackState(activeSession.getPlaybackState());
        updateMetadata(activeSession.getMetadata());
        show();
    }

    private final MediaController.Callback activeSessionCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            updateMetadata(metadata);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            updatePlaybackState(state);
        }
    };

    private void onActiveMediaSessionsChanged(@Nullable List<MediaController> mediaControllers) {
        Log.d(TAG, "media sessions update: " + mediaControllers);
        MediaController controller = mediaControllers != null && !mediaControllers.isEmpty() ? mediaControllers.get(0) : null;
        if (activeSession == controller) return;
        if (activeSession != null)
            activeSession.unregisterCallback(activeSessionCallback);
        if (controller != null)
            controller.registerCallback(activeSessionCallback, handler);

        activeSession = controller;
        updateNowPlaying();
    }

    private Optional<AppRecord> getActiveMediaApp() {
        if (activeSession == null) return Optional.empty();
        return getPackageBinder.get()
                .map(b -> b.getApp(activeSession.getPackageName()));
    }

    private void sendKeyPress(MediaController mediaController, int keyCode) {
        if (!mediaController.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
            Log.e(TAG, "media key down failed");

        if (!mediaController.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode)))
            Log.e(TAG, "media key up failed");
    }

    private void openNowPlayingApp() {
        getActiveMediaApp()
                .ifPresent(taskManager::dynamicOpen);
    }

    private void prevTrack() {
        if (activeSession == null) return;
        if ((activeSession.getFlags() & MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS) != 0) {
            Log.i(TAG, "using transport controls for skip prev");
            activeSession.getTransportControls().skipToPrevious();
            return;
        }

        Log.i(TAG, "using media button for skip prev");
        sendKeyPress(activeSession, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);
    }

    private void playPause() {
        if (activeSession == null) return;
        if ((activeSession.getFlags() & MediaSession.FLAG_HANDLES_MEDIA_BUTTONS) != 0) {
            Log.i(TAG, "using media button for play/pause");
            sendKeyPress(activeSession, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return;
        }
        Log.i(TAG, "using transport controls for play/pause");
        if (playing) {
            activeSession.getTransportControls().pause();
        } else {
            activeSession.getTransportControls().play();
        }
    }

    private void nextTrack() {
        if (activeSession == null) return;
        if ((activeSession.getFlags() & MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS) != 0) {
            Log.i(TAG, "using transport controls for skip next");
            activeSession.getTransportControls().skipToNext();
            return;
        }

        Log.i(TAG, "using media button for skip next");
        sendKeyPress(activeSession, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
    }

}
