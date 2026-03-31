package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.StringRes;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

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

    public boolean allowsStartProjectionWhenLocked() {
        return getBool(R.string.key_start_projection_when_locked, R.string.start_projection_when_locked_default);
    }

    public int getProjectionResumeGracePeriod() {
        return castInt(R.string.key_projection_resume_grace_period, R.string.projection_grace_period_default);
    }

    public int getVideoBufferSize() {
        return castInt(R.string.key_video_buffer_size, R.string.video_buffer_size_default);
    }

    private int castInt(@StringRes int key, @StringRes int defaultRes) {
        String stringValue = prefs.getString(context.getString(key), null);
        if (stringValue == null) stringValue = context.getString(defaultRes);

        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            Log.wtf(TAG, "failed to cast preference value to integer", e);
            assert false;

            try {
                return Integer.parseInt(context.getString(defaultRes));
            } catch (NumberFormatException ee) {
                Log.wtf(TAG, "default value failed to parse to int", ee);
                throw new AssertionError(e);
            }
        }
    }

    private boolean getBool(@StringRes int key, @StringRes int defaultRes) {
        boolean defaultValue = Boolean.parseBoolean(context.getString(defaultRes));
        return prefs.getBoolean(context.getString(key), defaultValue);
    }

    public static <T> T enumForPref(Context context, SharedPreferences prefs, @StringRes int key, @StringRes int defaultRes, List<Pair<Integer, T>> mapping) {
        String defaultValue = context.getString(defaultRes);
        String value = prefs.getString(
                context.getString(key),
                defaultValue);

        T defaultMapping = null;
        for (Pair<Integer, T> entry : mapping) {
            String entryValue = context.getString(entry.first);
            if (entryValue.equals(defaultValue)) defaultMapping = entry.second;
            if (entryValue.equals(value)) return entry.second;
        }

        if (defaultMapping == null)
            Log.wtf(TAG, "default value not present in mappings", new AssertionError());

        Log.wtf(TAG, "unhandled value for pref " + context.getString(key) + ": " + value);
        assert false;
        return defaultMapping;
    }

}
