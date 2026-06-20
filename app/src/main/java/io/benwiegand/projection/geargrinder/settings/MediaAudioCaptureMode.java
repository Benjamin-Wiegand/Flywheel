package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.util.Pair;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public enum MediaAudioCaptureMode {
    DISABLED,
    MEDIA_PROJECTION,
    REMOTE_SUBMIX;

    public static MediaAudioCaptureMode parse(Context context, String value) {
        return SettingsManager.enumForPref(
                context, value,
                R.string.key_media_audio_capture_mode,
                R.string.media_audio_capture_mode_default,
                List.of(
                        Pair.create(R.string.media_audio_capture_mode_disabled, DISABLED),
                        Pair.create(R.string.media_audio_capture_mode_media_projection, MEDIA_PROJECTION),
                        Pair.create(R.string.media_audio_capture_mode_remote_submix, REMOTE_SUBMIX)
                )
        );
    }
}
