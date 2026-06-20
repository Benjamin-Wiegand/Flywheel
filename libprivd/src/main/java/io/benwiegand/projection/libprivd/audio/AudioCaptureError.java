package io.benwiegand.projection.libprivd.audio;

import android.util.Log;

public enum AudioCaptureError {
    NO_ERROR,
    TRY_AGAIN,
    FAILURE,
    END_OF_STREAM;

    private static final String TAG = AudioCaptureError.class.getSimpleName();

    public static AudioCaptureError parse(int ordinal) {
        if (ordinal < 0 || ordinal >= AudioCaptureError.values().length) {
            Log.wtf(TAG, "parsing error, index out of range: " + ordinal);
            assert false;
            return FAILURE;
        }

        return values()[ordinal];
    }
}
