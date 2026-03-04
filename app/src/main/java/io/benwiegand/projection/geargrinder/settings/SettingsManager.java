package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import java.util.List;

public class SettingsManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static final String PREFERENCE_NAME = "io.benwiegand.projection.geargrinder_preferences";

    private final Context context;
    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public OperationalMode getOperationalMode() {
        return OperationalMode.read(context, prefs);
    }

    public PrivilegeMode getPrivilegeMode() {
        return PrivilegeMode.read(context, prefs);
    }

    public static <T> T enumForPref(Context context, SharedPreferences prefs, int key, int defaultValue, List<Pair<Integer, T>> mapping) {
        String value = prefs.getString(
                context.getString(key),
                context.getString(defaultValue));

        T defaultMapping = null;
        for (Pair<Integer, T> entry : mapping) {
            if (entry.first == defaultValue) defaultMapping = entry.second;
            if (!context.getString(entry.first).equals(value)) continue;
            return entry.second;
        }

        if (defaultMapping == null)
            Log.wtf(TAG, "default value not present in mappings");

        Log.wtf(TAG, "unhandled value for pref " + context.getString(key) + ": " + value);
        assert false;
        return defaultMapping;
    }

}
