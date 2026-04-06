package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.util.LocaleUtil;

public class UserFriendlyException extends Exception {
    private final String friendlyTitle;
    private final String friendlyMessage;

    private static String extractExceptionMessage(Context appContext, @StringRes int message) {
        // make the actual exception message english, despite allowing it to be translated for the UI
        return LocaleUtil.getDeveloperContext(appContext)
                .getString(message);
    }

    public UserFriendlyException(Context c, @StringRes int message) {
        super(extractExceptionMessage(c, message));
        friendlyTitle = null;
        friendlyMessage = c.getString(message);
    }

    public UserFriendlyException(Context c, @StringRes int message, Throwable cause) {
        super(extractExceptionMessage(c, message), cause);
        friendlyTitle = null;
        friendlyMessage = c.getString(message);
    }

    public UserFriendlyException(Context c, @StringRes int title, @StringRes int message) {
        super(extractExceptionMessage(c, message));
        friendlyTitle = c.getString(title);
        friendlyMessage = c.getString(message);
    }

    public UserFriendlyException(Context c, @StringRes int title, @StringRes int message, Throwable cause) {
        super(extractExceptionMessage(c, message), cause);
        friendlyTitle = c.getString(title);
        friendlyMessage = c.getString(message);
    }

    @Nullable
    public String getFriendlyTitle() {
        return friendlyTitle;
    }

    public String getFriendlyMessage() {
        return friendlyMessage;
    }

    @Nullable
    @Override
    public String getLocalizedMessage() {
        return friendlyMessage;
    }
}
