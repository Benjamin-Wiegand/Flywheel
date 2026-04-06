package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class CorruptedCertificateException extends UserFriendlyException {
    public CorruptedCertificateException(Context c, int message) {
        super(c, R.string.corrupted_cert_error_title, message);
    }

    public CorruptedCertificateException(Context c, int message, Throwable cause) {
        super(c, R.string.corrupted_cert_error_title, message, cause);
    }
}
