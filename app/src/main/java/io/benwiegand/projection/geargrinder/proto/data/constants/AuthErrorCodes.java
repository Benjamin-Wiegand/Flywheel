package io.benwiegand.projection.geargrinder.proto.data.constants;

import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.R;

public class AuthErrorCodes {
    public static long GENERAL_FAILURE = -3;
    public static long CERTIFICATE_NOT_VALID_YET = -23;
    public static long CERTIFICATE_NOT_VALID_ANYMORE = -24;

    @StringRes
    public static int getMessageForErrorCode(long code) {
        if (code == GENERAL_FAILURE) {
            return R.string.auth_error_general;
        } else if (code == CERTIFICATE_NOT_VALID_YET) {
            return R.string.auth_error_cert_not_valid_yet;
        } else if (code == CERTIFICATE_NOT_VALID_ANYMORE) {
            return R.string.auth_error_cert_not_valid_anymore;
        }
        return R.string.auth_error_unknown;
    }
}
