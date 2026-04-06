package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.R;

public class FileImportException extends UserFriendlyException {
    public FileImportException(Context c, @StringRes int message, Throwable cause) {
        super(c, R.string.file_import_error_title, message, cause);
    }

    public FileImportException(Context c, @StringRes int message) {
        super(c, R.string.file_import_error_title, message);
    }
}
