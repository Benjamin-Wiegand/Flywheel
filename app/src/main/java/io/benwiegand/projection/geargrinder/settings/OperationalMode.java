package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public enum OperationalMode {
    AUDIO_ONLY,
    SCREEN_MIRRORING,
    GEARGRINDER_PROJECTION;

    public static OperationalMode read(Context context, SharedPreferences prefs) {
        return SettingsManager.enumForPref(
                context, prefs,
                R.string.key_operational_mode,
                R.string.operational_mode_screen_mirroring,
                List.of(
                        Pair.create(R.string.operational_mode_audio_only, AUDIO_ONLY),
                        Pair.create(R.string.operational_mode_screen_mirroring, SCREEN_MIRRORING),
                        Pair.create(R.string.operational_mode_geargrinder_projection, GEARGRINDER_PROJECTION)
                )
        );
    }
}
