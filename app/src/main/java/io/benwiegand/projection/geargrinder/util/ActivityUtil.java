package io.benwiegand.projection.geargrinder.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.StringRes;

public class ActivityUtil {
    private static final String TAG = ActivityUtil.class.getSimpleName();

    public static void tryLaunchIntents(Activity activity, Intent... intents) {
        for (Intent intent : intents) {
            if (intent == null) continue;
            try {
                Log.d(TAG, "trying intent: " + intent);
                activity.startActivity(intent);
                return;
            } catch (Throwable t) {
                Log.e(TAG, "intent failed: " + intent, t);
            }
        }

        Log.wtf(TAG, "all intents failed to launch", new AssertionError());
    }

    public static void openLink(Activity activity, String uri) {
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
    }

    public static void openLink(Activity activity, @StringRes int uriRes) {
        openLink(activity, activity.getString(uriRes));
    }
}
