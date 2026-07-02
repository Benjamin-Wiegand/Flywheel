package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class PrivdLaunchException extends UserFriendlyException {
    public PrivdLaunchException(Context c, int message, Throwable cause) {
        super(c, R.string.privd_launch_error_title, message, cause);
    }

    public PrivdLaunchException(Context c, int message) {
        super(c, R.string.privd_launch_error_title, message);
    }
}
