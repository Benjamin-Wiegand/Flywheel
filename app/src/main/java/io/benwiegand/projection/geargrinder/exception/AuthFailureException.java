package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.proto.data.constants.AuthErrorCodes;

public class AuthFailureException extends UserFriendlyException {
    private final long code;

    public AuthFailureException(Context c, long code) {
        super(
                "authentication error, code = " + code,
                c.getString(R.string.auth_error_title),
                c.getString(R.string.auth_error_message_format, code, c.getString(AuthErrorCodes.getMessageForErrorCode(code))),
                null);
        this.code = code;
    }
}
