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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

    private static final boolean LOG_DEBUG = false;

    private final Context context;
    private final View root;
    private final Handler handler;

    private final ProjectionTaskManager taskManager;
    private final Supplier<Optional<PackageService.ServiceBinder>> getPackageBinder;

    private final ResourceLoaderThread artLoaderThread;
    private Object artLoadToken = new Object();

    private final ComponentName notificationListenerComponent;
    private final MediaSessionManager mediaSessionManager;

    private final Deque<SessionWrapper> orderedMediaSessions = new LinkedList<>();

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
        root.findViewById(R.id.skip_prev_button).setOnClickListener(v -> {
            SessionWrapper session = getActiveSession();
            if (session == null) return;
            session.skipPrev();
        });
        root.findViewById(R.id.play_pause_button).setOnClickListener(v -> {
            SessionWrapper session = getActiveSession();
            if (session == null) return;
            session.playPause();
        });
        root.findViewById(R.id.skip_next_button).setOnClickListener(v -> {
            SessionWrapper session = getActiveSession();
            if (session == null) return;
            session.skipNext();
        });
    }

    public void destroy() {
        mediaSessionManager.removeOnActiveSessionsChangedListener(this::onActiveMediaSessionsChanged);
        for (SessionWrapper session : orderedMediaSessions)
            session.destroy();
    }

    private void hide() {
        root.setVisibility(View.GONE);
    }

    private void show() {
        root.setVisibility(View.VISIBLE);
    }

    private void updatePlaybackState(SessionWrapper session) {
        ImageButton skipPrevButton = root.findViewById(R.id.skip_prev_button);
        ImageButton playPauseButton = root.findViewById(R.id.play_pause_button);
        ImageButton skipNextButton = root.findViewById(R.id.skip_next_button);
        View bufferingIndicator = root.findViewById(R.id.buffering_indicator);
        View errorIndicator = root.findViewById(R.id.playback_error_indicator);

        playPauseButton.setImageResource(session.isPlaying() ? R.drawable.pause : R.drawable.play_arrow);
        bufferingIndicator.setVisibility(session.isLoading() ? View.VISIBLE : View.GONE);
        errorIndicator.setVisibility(session.isError() ? View.VISIBLE : View.GONE);

        PlaybackState playbackState = session.getPlaybackState();
        long actions = playbackState != null ? playbackState.getActions() : 0;
        skipPrevButton.setVisibility((actions & ACTION_SKIP_TO_PREVIOUS) != 0 ? View.VISIBLE : View.GONE);
        skipNextButton.setVisibility((actions & ACTION_SKIP_TO_NEXT) != 0 ? View.VISIBLE : View.GONE);
    }

    private void updateMetadata(SessionWrapper session) {
        MediaMetadata metadata = session != null ? session.getMetadata() : null;
        ImageView artImageView = root.findViewById(R.id.media_art_image);
        artImageView.setImageResource(R.drawable.music);
        if (metadata == null) return;

        Object token = new Object();
        artLoadToken = token;
        artLoaderThread.execute(() -> {
            Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (bitmap == null) bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            int imageSize = (int) dpToPx(context, 48);
            return Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, true);
        }, bitmap -> {
            if (bitmap == null) return;
            if (artLoadToken != token) return;
            artImageView.setImageBitmap(bitmap);
        });
    }

    private void updateNowPlaying() {
        SessionWrapper activeSession = getActiveSession();
        if (activeSession == null) {
            updateMetadata(null);
            hide();
            return;
        }
        updatePlaybackState(activeSession);
        updateMetadata(activeSession);
        show();
    }

    private void onActiveMediaSessionsChanged(@Nullable List<MediaController> mediaControllers) {
        Log.d(TAG, "media sessions update: " + mediaControllers);
        if (mediaControllers == null) {
            try {
                mediaControllers = mediaSessionManager.getActiveSessions(notificationListenerComponent);
                Log.d(TAG, "session list: " + mediaControllers);
            } catch (SecurityException e) {
                Log.e(TAG, "missing permission to get media sessions: " + e);
                mediaControllers = List.of();
            }
        }

        if (LOG_DEBUG) {
            for (MediaController controller : mediaControllers) {
                Log.d(TAG, " - " + controller.getPackageName());
                Log.d(TAG, " --- " + controller.getPlaybackState());
            }
        }

        List<SessionWrapper> oldSessions = List.copyOf(orderedMediaSessions);
        orderedMediaSessions.clear();

        for (MediaController controller : mediaControllers) {
            MediaSession.Token token = controller.getSessionToken();
            SessionWrapper session = null;
            for (SessionWrapper oldSession : oldSessions) {
                if (!token.equals(oldSession.getToken())) continue;
                session = oldSession;
                break;
            }

            if (session == null) session = new SessionWrapper(controller);
            orderedMediaSessions.add(session);
        }

        updateNowPlaying();
    }

    private void promoteSessionToActive(SessionWrapper session) {
        Log.v(TAG, "promoting session to active: " + session);
        orderedMediaSessions.remove(session);
        orderedMediaSessions.addFirst(session);
        updateNowPlaying();
    }

    private SessionWrapper getActiveSession() {
        return orderedMediaSessions.peekFirst();
    }

    private MediaController getActiveMediaController() {
        SessionWrapper session = getActiveSession();
        if (session == null) return null;
        return session.getController();
    }

    private Optional<AppRecord> getActiveMediaApp() {
        MediaController activeSession = getActiveMediaController();
        if (activeSession == null) return Optional.empty();
        return getPackageBinder.get()
                .map(b -> b.getApp(activeSession.getPackageName()));
    }

    private void openNowPlayingApp() {
        getActiveMediaApp()
                .ifPresent(taskManager::dynamicOpen);
    }

    private class SessionWrapper extends MediaController.Callback {
        private final MediaController controller;
        private int state = STATE_NONE;

        public SessionWrapper(MediaController controller) {
            this.controller = controller;
            PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null) state = playbackState.getState();

            controller.registerCallback(this, handler);
        }

        public void destroy() {
            controller.unregisterCallback(this);
        }

        public boolean isPlaying() {
            return switch (state) {
                case STATE_PLAYING,
                     STATE_FAST_FORWARDING,
                     STATE_REWINDING,
                     STATE_BUFFERING,
                     STATE_CONNECTING,
                     STATE_SKIPPING_TO_PREVIOUS,
                     STATE_SKIPPING_TO_QUEUE_ITEM,
                     STATE_SKIPPING_TO_NEXT -> true;
                case STATE_PAUSED,
                     STATE_STOPPED,
                     STATE_NONE,
                     STATE_ERROR -> false;
                default -> {
                    Log.wtf(TAG, "unhandled playback state: " + state, new AssertionError());
                    yield false;
                }
            };
        }

        public boolean isLoading() {
            return switch (state) {
                case STATE_BUFFERING,
                     STATE_CONNECTING,
                     STATE_SKIPPING_TO_PREVIOUS,
                     STATE_SKIPPING_TO_QUEUE_ITEM,
                     STATE_SKIPPING_TO_NEXT -> true;
                default -> false;
            };
        }

        public boolean isError() {
            return state == STATE_ERROR;
        }

        public MediaController getController() {
            return controller;
        }

        public PlaybackState getPlaybackState() {
            return controller.getPlaybackState();
        }

        public MediaMetadata getMetadata() {
            return controller.getMetadata();
        }

        public MediaSession.Token getToken() {
            return controller.getSessionToken();
        }

        private void sendKeyPress(MediaController mediaController, int keyCode) {
            if (!mediaController.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode)))
                Log.e(TAG, "media key down failed");

            if (!mediaController.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode)))
                Log.e(TAG, "media key up failed");
        }

        public void skipPrev() {
            if ((controller.getFlags() & MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS) != 0) {
                Log.i(TAG, "using transport controls for skip prev");
                controller.getTransportControls().skipToPrevious();
                return;
            }

            Log.i(TAG, "using media button for skip prev");
            sendKeyPress(controller, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        public void playPause() {
            if ((controller.getFlags() & MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS) != 0) {
                Log.i(TAG, "using transport controls for play/pause");
                if (isPlaying()) {
                    controller.getTransportControls().pause();
                } else {
                    controller.getTransportControls().play();
                }
                return;
            }

            Log.i(TAG, "using media button for play/pause");
            sendKeyPress(controller, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        public void skipNext() {
            if ((controller.getFlags() & MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS) != 0) {
                Log.i(TAG, "using transport controls for skip next");
                controller.getTransportControls().skipToNext();
                return;
            }

            Log.i(TAG, "using media button for skip next");
            sendKeyPress(controller, KeyEvent.KEYCODE_MEDIA_NEXT);
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadata metadata) {
            if (getActiveSession() == this)
                updateMetadata(this);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState playbackState) {
            SessionWrapper activeSession = getActiveSession();
            boolean wasPlaying = isPlaying();
            state = playbackState != null ? playbackState.getState() : STATE_NONE;
            boolean playingTransition = isPlaying() && !wasPlaying;
            boolean playingInactive = isPlaying() && activeSession != null && !activeSession.isPlaying();
            if (activeSession != this && (playingInactive || playingTransition)) {
                // this happens due to race conditions in the media session callback api
                if (playingInactive) Log.d(TAG, "non-active session is playing while active session isn't");
                if (playingTransition) Log.d(TAG, "non-active session just started playing");
                promoteSessionToActive(this);
                return;
            }

            if (getActiveSession() == this)
                updatePlaybackState(this);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SessionWrapper that)) return false;
            return Objects.equals(controller.getSessionToken(), that.controller.getSessionToken());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(controller.getSessionToken().hashCode());
        }

        @NonNull
        @Override
        public String toString() {
            return controller.getPackageName() + ": " + getPlaybackState();
        }
    }

}
