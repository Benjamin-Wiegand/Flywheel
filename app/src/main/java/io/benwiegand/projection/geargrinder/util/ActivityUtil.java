package io.benwiegand.projection.geargrinder.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class ActivityUtil {
    private static final String TAG = ActivityUtil.class.getSimpleName();

    public static final String SOURCE_CODE_LINK = "https://github.com/Benjamin-Wiegand/Flywheel";
    public static final String WIKI_LINK = "https://github.com/Benjamin-Wiegand/Flywheel/wiki";
    public static final String LICENSE_LINK = "https://github.com/Benjamin-Wiegand/Flywheel/blob/master/LICENSE";
    public static final String ISSUE_TRACKER_LINK = "https://github.com/Benjamin-Wiegand/Flywheel/issues";
    public static final String CERTIFICATES_AND_KEYS_INFO_LINK = "https://github.com/Benjamin-Wiegand/Flywheel/wiki/Certificates-and-Keys";

    public static final String SHIZUKU_DOWNLOAD_LINK = "https://github.com/RikkaApps/Shizuku/releases/latest";

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
}
