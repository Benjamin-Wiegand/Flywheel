package io.benwiegand.projection.geargrinder.projection.audio;

import static android.media.AudioAttributes.USAGE_ALARM;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import io.benwiegand.projection.geargrinder.channel.AudioChannel;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;
import io.benwiegand.projection.libprivd.audio.AudioCapture;
import io.benwiegand.projection.libprivd.audio.AudioRecordCapture;

public class LocalAudioRecordCapture extends AudioRecordCapture {

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public LocalAudioRecordCapture(AudioPlaybackCaptureConfiguration config, AudioPreset preset, int bufferSize) {
        super(new AudioRecord.Builder()
                .setAudioFormat(preset.createAudioFormat())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build());

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static class MediaProjectionProvider implements AudioChannel.AudioCaptureProvider {
        private static final String TAG = MediaProjectionProvider.class.getSimpleName();

        private final Object lock = new Object();

        private final Context context;
        private Runnable onReady = null;
        private MediaProjection mediaProjection = null;

        public MediaProjectionProvider(Context context) {
            this.context = context;
        }

        public void setMediaProjection(MediaProjection mediaProjection) {
            synchronized (lock) {
                this.mediaProjection = mediaProjection;
                if (onReady != null) {
                    onReady.run();
                    onReady = null;
                }
            }
        }

        @Override
        public void registerReadyCallback(Runnable onReady) {
            synchronized (lock) {
                if (mediaProjection != null) {
                    onReady.run();
                    return;
                }

                this.onReady = onReady;
            }
        }

        @Override
        public AudioCapture getInstance(AudioPreset audioPreset, int bufferSize) {
            if (mediaProjection == null) {
                Log.wtf(TAG, "getInstance() called before onReady!!!");
                assert false;
                return null;
            }

            try {
                return new LocalAudioRecordCapture(
                        new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                                .excludeUsage(USAGE_ALARM)
                                .build(),
                        audioPreset, bufferSize
                );
            } catch (SecurityException e) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: error message
                    Log.e(TAG, "failed to start audio capture for media projection. RECORD_AUDIO may need to be explicitly granted", e);
                    return null;
                } else {
                    throw e;
                }
            }
        }
    }

}
