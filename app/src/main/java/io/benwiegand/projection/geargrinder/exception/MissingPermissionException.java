package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class MissingPermissionException extends UserFriendlyException {

    private final String permissionKey;

    public MissingPermissionException(Context c, int message, String permissionKey) {
        super(c, R.string.missing_permission_error_title, message);
        this.permissionKey = permissionKey;
    }

    public MissingPermissionException(Context c, int message, Throwable cause, String permissionKey) {
        super(c, R.string.missing_permission_error_title, message, cause);
        this.permissionKey = permissionKey;
    }

    public String getPermissionKey() {
        return permissionKey;
    }
}
