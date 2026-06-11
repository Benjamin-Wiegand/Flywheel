package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class BluetoothConnectionException extends UserFriendlyException {
    public BluetoothConnectionException(Context c, int message, Throwable cause) {
        super(c, R.string.bluetooth_connection_error_title, message, cause);
    }

    public BluetoothConnectionException(Context c, int message) {
        super(c, R.string.bluetooth_connection_error_title, message);
    }
}
