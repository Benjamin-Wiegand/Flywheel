package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class CorruptedCertificateKeyBundleException extends UserFriendlyException {
    public CorruptedCertificateKeyBundleException(Context c, int message, Throwable cause) {
        super(c, R.string.corrupted_cert_key_bundle_error_title, message, cause);
    }

    public CorruptedCertificateKeyBundleException(Context c, int message) {
        super(c, R.string.corrupted_cert_key_bundle_error_title, message);
    }
}
