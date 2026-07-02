package io.benwiegand.projection.geargrinder.projection;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.channel.AudioChannel;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.projection.audio.PrivdAudioCaptureProxy;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import io.benwiegand.projection.libprivd.IPrivd;

public class PrivdAudioService implements GeargrinderServiceConnector.ConnectionListener, IPCConnectionListener {
    private static final String TAG = PrivdAudioService.class.getSimpleName();

    private final GeargrinderServiceConnector connector;

    private final PrivdAudioCaptureProxy.PrivdProvider mediaAudioCaptureProvider = new PrivdAudioCaptureProxy.PrivdProvider(MediaRecorder.AudioSource.REMOTE_SUBMIX);
    private final PrivdAudioCaptureProxy.PrivdProvider phoneAudioCaptureProvider = new PrivdAudioCaptureProxy.PrivdProvider(MediaRecorder.AudioSource.VOICE_DOWNLINK);

    private boolean dead = false;

    public PrivdAudioService(Context context) {
        connector = new GeargrinderServiceConnector(TAG, context, this);
        connector.bindPrivdService(BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    public void destroy() {
        if (dead) return;
        dead = true;
        connector.destroy();
    }

    public AudioChannel.AudioCaptureProvider getMediaAudioCaptureProvider() {
        return mediaAudioCaptureProvider;
    }

    public PrivdAudioCaptureProxy.PrivdProvider getPhoneAudioCaptureProvider() {
        return phoneAudioCaptureProvider;
    }

    @Override
    public void onPrivdServiceConnected(PrivdService.ServiceBinder binder) {
        binder.requestDaemon(this);
    }

    @Override
    public void onPrivdConnected(IPrivd privd) {
        if (dead) return;

        Log.i(TAG, "privd connected");
        mediaAudioCaptureProvider.setPrivd(privd);
        phoneAudioCaptureProvider.setPrivd(privd);
    }

    @Override
    public void onPrivdDisconnected() {
        // TODO
        Log.e(TAG, "privd connection lost");
    }

    @Override
    public void onPrivdLaunchFailure(UserFriendlyException e) {
        // TODO
        Log.e(TAG, "privd failed to launch", e);
    }
}
