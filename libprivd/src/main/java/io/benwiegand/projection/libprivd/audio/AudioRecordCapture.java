package io.benwiegand.projection.libprivd.audio;

import static android.media.AudioTimestamp.TIMEBASE_BOOTTIME;

import static io.benwiegand.projection.libprivd.audio.AudioCaptureError.END_OF_STREAM;
import static io.benwiegand.projection.libprivd.audio.AudioCaptureError.FAILURE;
import static io.benwiegand.projection.libprivd.audio.AudioCaptureError.NO_ERROR;
import static io.benwiegand.projection.libprivd.audio.AudioCaptureError.TRY_AGAIN;

import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.util.Log;

public class AudioRecordCapture implements AudioCapture {
    private static final String TAG = AudioRecordCapture.class.getSimpleName();

    private final AudioRecord audioRecord;
    private final AudioTimestamp timestamp;

    public AudioRecordCapture(AudioRecord audioRecord) {
        this.audioRecord = audioRecord;
        timestamp = new AudioTimestamp();
    }

    @Override
    public void begin() {
        audioRecord.startRecording();
    }

    @Override
    public void destroy() {
        audioRecord.release();
    }

    @Override
    public void nextBuffer(AudioCaptureResult result, byte[] buffer, int offset, int length) {
        int ret;

        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            Log.w(TAG, "not initialized yet");
            result.error = TRY_AGAIN;
            return;
        }

        ret = audioRecord.getTimestamp(timestamp, TIMEBASE_BOOTTIME);
        switch (ret) {
            case AudioRecord.SUCCESS -> {}
            case AudioRecord.ERROR_INVALID_OPERATION -> {
                // not ready yet
                result.error = TRY_AGAIN;
                return;
            }
            default -> {
                Log.wtf(TAG, "unexpected error code while getting timestamp: " + ret);
                result.error = TRY_AGAIN;
                return;
            }
        }

        ret = audioRecord.read(buffer, offset, length);
        if (ret < 0) {
            Log.e(TAG, "AudioRecord error: " + ret);
            switch (ret) {
                case AudioRecord.ERROR,
                     AudioRecord.ERROR_BAD_VALUE -> {
                    result.error = FAILURE;
                    return;
                }
                case AudioRecord.ERROR_INVALID_OPERATION -> {
                    result.error = TRY_AGAIN;
                    return;
                }
                case AudioRecord.ERROR_DEAD_OBJECT -> {
                    result.error = END_OF_STREAM;
                    return;
                }
            }
        } else if (ret == 0) {
            Log.w(TAG, "empty buffer");
            result.error = TRY_AGAIN;
            return;
        }

        boolean silent = true;
        for (int i = offset; i < offset + length; i++) {
            if (buffer[i] == 0) continue;
            silent = false;
        }

        result.error = NO_ERROR;
        result.length = ret;
        result.timestamp = timestamp.nanoTime;
        result.silent = silent;
    }
}
