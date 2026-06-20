package io.benwiegand.projection.geargrinder.projection.audio;

import android.os.RemoteException;
import android.util.Log;

import io.benwiegand.projection.geargrinder.channel.AudioChannel;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;
import io.benwiegand.projection.libprivd.IPrivd;
import io.benwiegand.projection.libprivd.audio.AudioCapture;
import io.benwiegand.projection.libprivd.audio.AudioCaptureResult;

public class PrivdAudioCaptureProxy implements AudioCapture {
    private static final String TAG = PrivdAudioCaptureProxy.class.getSimpleName();

    private final IPrivd privd;
    private final int id;

    public PrivdAudioCaptureProxy(IPrivd privd, AudioPreset preset, int bufferSize, int audioSource) throws RemoteException {
        this.privd = privd;

        id = privd.createPrivilegedAudioRecordCapture(preset.createAudioFormat(), bufferSize, audioSource);
    }

    @Override
    public void begin() {
        try {
            privd.audioCaptureBegin(id);
        } catch (RemoteException e) {
            throw new RuntimeException("failed to begin audio capture", e);
        }
    }

    @Override
    public void destroy() {
        try {
            privd.destroyAudioCapture(id);
        } catch (RemoteException e) {
            Log.w(TAG, "failed to destroy audio capture", e);
            // don't throw, privd is probably dead and the audio capture is destroyed anyway
        }
    }

    @Override
    public void nextBuffer(AudioCaptureResult result, byte[] buffer, int offset, int length) {
        try {
            privd.audioCaptureNextBuffer(id, result, buffer, offset, length);
        } catch (RemoteException e) {
            throw new RuntimeException("failed to get next buffer for audio capture", e);
        }
    }

    public static class PrivdProvider implements AudioChannel.AudioCaptureProvider {
        private static final String TAG = LocalAudioRecordCapture.MediaProjectionProvider.class.getSimpleName();

        private final Object lock = new Object();

        private final int audioSource;
        private Runnable onReady = null;
        private IPrivd privd = null;

        public PrivdProvider(int audioSource) {
            this.audioSource = audioSource;
        }

        public void setPrivd(IPrivd privd) {
            synchronized (lock) {
                this.privd = privd;
                if (onReady != null) {
                    onReady.run();
                    onReady = null;
                }
            }
        }

        @Override
        public void registerReadyCallback(Runnable onReady) {
            synchronized (lock) {
                if (privd != null) {
                    onReady.run();
                    return;
                }

                this.onReady = onReady;
            }
        }

        @Override
        public AudioCapture getInstance(AudioPreset audioPreset, int bufferSize) {
            if (privd == null) {
                Log.wtf(TAG, "getInstance() called before onReady!!!");
                assert false;
                return null;
            }

            try {
                return new PrivdAudioCaptureProxy(privd, audioPreset, bufferSize, audioSource);
            } catch (Throwable t) {
                Log.e(TAG, "failed to initialize privd audio capture proxy", t);
                return null;
            }
        }
    }
}
