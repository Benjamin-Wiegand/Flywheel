package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class CorruptedKeyException extends UserFriendlyException {
    public CorruptedKeyException(Context c, int message, Throwable cause) {
        super(c, R.string.corrupted_key_error_title, message, cause);
    }

    public CorruptedKeyException(Context c, int message) {
        super(c, R.string.corrupted_key_error_title, message);
    }
}
