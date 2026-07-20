package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.StringRes;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public enum PrivilegeMode {
    NO_ROOT,
    SHIZUKU,
    ROOT;

    private static final List<Pair<Integer, PrivilegeMode>> PRIVILEGE_MODE_VALUE_MAPPING = List.of(
            Pair.create(R.string.privilege_mode_no_root, NO_ROOT),
            Pair.create(R.string.privilege_mode_shizuku, SHIZUKU),
            Pair.create(R.string.privilege_mode_root, ROOT));

    public static PrivilegeMode parse(Context context, String value) {
        return SettingsManager.enumForPref(
                context, value,
                R.string.key_privilege_mode,
                R.string.privilege_mode_default,
                PRIVILEGE_MODE_VALUE_MAPPING
        );
    }

    public @StringRes int preferenceValueRes() {
        for (Pair<Integer, PrivilegeMode> pair : PRIVILEGE_MODE_VALUE_MAPPING) {
            if (pair.second != this) continue;
            return pair.first;
        }
        throw new AssertionError("unmapped privilege mode enum");
    }
}
