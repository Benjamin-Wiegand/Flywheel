package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class ProjectionException extends UserFriendlyException {
    public ProjectionException(Context c, int message) {
        super(c, R.string.projection_error_title, message);
    }

    public ProjectionException(Context c, int message, Throwable cause) {
        super(c, R.string.projection_error_title, message, cause);
    }
}
