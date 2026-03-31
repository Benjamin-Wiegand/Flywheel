package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public enum PrivilegeMode {
    NO_ROOT,
    SHIZUKU,
    ROOT;

    public static PrivilegeMode read(Context context, SharedPreferences prefs) {
        return SettingsManager.enumForPref(
                context, prefs,
                R.string.key_privilege_mode,
                R.string.privilege_mode_default,
                List.of(
                        Pair.create(R.string.privilege_mode_no_root, NO_ROOT),
                        Pair.create(R.string.privilege_mode_shizuku, SHIZUKU),
                        Pair.create(R.string.privilege_mode_root, ROOT)
                )
        );
    }
}
