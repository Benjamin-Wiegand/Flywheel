package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import androidx.annotation.NonNull;

import io.benwiegand.projection.geargrinder.R;

public class ConnectionError extends UserFriendlyException {
    public ConnectionError(Context c, int message) {
        super(c, R.string.connection_error_title, message);
    }

    public ConnectionError(Context c, int message, Throwable cause) {
        super(c, R.string.connection_error_title, message, cause);
    }

    @NonNull
    @Override
    public synchronized ConnectionError fillInStackTrace() {
        return (ConnectionError) super.fillInStackTrace();
    }
}
