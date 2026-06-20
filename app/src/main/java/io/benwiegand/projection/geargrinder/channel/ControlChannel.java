package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.*;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import javax.net.ssl.SSLException;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.projection.PrivdAudioService;
import io.benwiegand.projection.geargrinder.projection.audio.LocalAudioRecordCapture;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.proto.data.readable.AudioFocusResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.PingRequest;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.AudioChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.sensor.SensorChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.writable.AudioFocusRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.PingResponse;
import io.benwiegand.projection.geargrinder.proto.data.writable.ServiceDiscoveryRequest;
import io.benwiegand.projection.geargrinder.proto.data.readable.ServiceDiscoveryResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoChannelMeta;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class ControlChannel implements MessageListener, ProjectionService.Listener {
    private static final String TAG = ControlChannel.class.getSimpleName();

    private static final int VERSION_CODE_MAJOR = 1;
    private static final int VERSION_CODE_MINOR = 7;

    private final Object lock = new Object();

    private final Context context;
    private final ConnectionService.ServiceBinder connectionServiceBinder;
    private final MessageBroker mb;
    private final TLSService tlsService;
    private final SettingsManager settingsManager;

    private final MessageBroker.MessageSendParameters unencryptedParams;
    private final MessageBroker.MessageSendParameters encryptedParams;

    private final ControlListener controlListener;

    private PrivdAudioService privdAudioService = null;

    private ServiceDiscoveryResponse serviceDiscoveryResponse = null;

    private VideoChannel videoChannel = null;
    private AudioChannel mediaAudioChannel = null;
    private AudioChannel speechAudioChannel = null;
    private InputChannel inputChannel = null;
    private SensorChannel sensorChannel = null;

    public ControlChannel(Context context, MessageBroker mb, TLSService tlsService, ControlListener controlListener, SettingsManager settingsManager, ConnectionService.ServiceBinder connectionServiceBinder) {
        this.context = context;
        this.connectionServiceBinder = connectionServiceBinder;
        this.mb = mb;
        this.tlsService = tlsService;
        this.controlListener = controlListener;
        this.settingsManager = settingsManager;

        unencryptedParams = new MessageBroker.MessageSendParameters(CHANNEL_CONTROL, false, false);
        encryptedParams = new MessageBroker.MessageSendParameters(CHANNEL_CONTROL, true, false);
    }

    public void destroy() {
        if (videoChannel != null) videoChannel.destroy();
        if (mediaAudioChannel != null) mediaAudioChannel.destroy();
        if (speechAudioChannel != null) speechAudioChannel.destroy();
        if (inputChannel != null) inputChannel.destroy();
        if (sensorChannel != null) sensorChannel.destroy();
        if (privdAudioService != null) privdAudioService.destroy();
    }

    private void setupPrivdAudioService() {
        synchronized (lock) {
            if (privdAudioService != null) return;

            Log.i(TAG, "setting up privd audio service");
            privdAudioService = new PrivdAudioService(context);
        }
    }

    private AudioChannel.AudioCaptureProvider createMediaAudioCaptureProvider() {
        return switch (settingsManager.getMediaAudioCaptureMode()) {
            case DISABLED -> {
                Log.w(TAG, "media audio capture is disabled");
                yield null;
            }
            case MEDIA_PROJECTION -> {
                Log.i(TAG, "using media projection for media audio capture");
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Log.e(TAG, "can't launch audio capture through MediaProjection: android version too old");
                    yield null;
                }

                LocalAudioRecordCapture.MediaProjectionProvider captureProvider = new LocalAudioRecordCapture.MediaProjectionProvider(context);
                connectionServiceBinder.requestMediaProjection(mediaProjection -> {
                    if (!mb.isAlive()) return;
                    captureProvider.setMediaProjection(mediaProjection);
                });

                yield captureProvider;
            }
            case REMOTE_SUBMIX -> {
                Log.i(TAG, "using REMOTE_SUBMIX for media audio capture");
                setupPrivdAudioService();
                yield privdAudioService.getMediaAudioCaptureProvider();
            }
        };
    }

    private AudioChannel.AudioCaptureProvider createPhoneAudioCaptureProvider() {
        if (!settingsManager.allowCapturePhoneAudio()) {
            Log.w(TAG, "phone audio capture is disabled");
            return null;
        }

        Log.i(TAG, "using VOICE_CALL for phone audio capture");
        setupPrivdAudioService();
        return privdAudioService.getPhoneAudioCaptureProvider();
    }

    private void startProjection() {
        controlListener.onCarNameDiscovered(serviceDiscoveryResponse.friendlyName());

        ProjectionService projectionService = connectionServiceBinder.getOrCreateGeargrinderProjectionService(this);

        mb.sendMessage(encryptedParams, CMD_AUDIO_FOCUS_REQUEST, new AudioFocusRequest(AudioFocusRequest.Type.GAIN).serialize());

        Log.i(TAG, "initializing channels");
        for (ChannelMeta channelMeta : serviceDiscoveryResponse.channelMetadata()) switch (channelMeta) {
            case VideoChannelMeta vcm -> {
                if (videoChannel != null) {     // I don't know of any cars that do multi-display, but I wouldn't be surprised
                    Log.w(TAG, "multiple video channels!");
                    Log.w(TAG, "not initializing: " + vcm);
                    continue;
                }
                Log.d(TAG, "init video channel: " + vcm);
                videoChannel = new VideoChannel(mb, projectionService, settingsManager, vcm);
                videoChannel.openChannel();
            }
            case AudioChannelMeta acm -> {
                switch (acm.audioType()) {
                    case SPEECH -> {
                        if (speechAudioChannel != null) {    // this probably won't happen
                            Log.wtf(TAG, "multiple speech audio channels!");
                            Log.w(TAG, "not initializing: " + acm);
                            continue;
                        }

                        AudioChannel.AudioCaptureProvider audioCaptureProvider = createPhoneAudioCaptureProvider();
                        if (audioCaptureProvider == null) {
                            Log.w(TAG, "no phone audio capture provider for speech audio channel");
                            Log.w(TAG, "not initializing: " + acm);
                            continue;
                        }

                        Log.d(TAG, "init speech audio channel: " + acm);
                        speechAudioChannel = new AudioChannel(mb, acm, audioCaptureProvider);
                        speechAudioChannel.openChannel();
                    }
                    case MEDIA -> {
                        if (mediaAudioChannel != null) {    // this probably won't happen
                            Log.wtf(TAG, "multiple media audio channels!");
                            Log.w(TAG, "not initializing: " + acm);
                            continue;
                        }

                        AudioChannel.AudioCaptureProvider audioCaptureProvider = createMediaAudioCaptureProvider();
                        if (audioCaptureProvider == null) {
                            Log.w(TAG, "no audio capture provider for media audio channel");
                            Log.w(TAG, "not initializing: " + acm);
                            continue;
                        }

                        Log.d(TAG, "init media audio channel: " + acm);
                        mediaAudioChannel = new AudioChannel(mb, acm, audioCaptureProvider);
                        mediaAudioChannel.openChannel();
                    }
                    default -> {
                        Log.w(TAG, "audio channel type not currently supported/handled: " + acm.audioType());
                        Log.w(TAG, "not initializing: " + acm);
                    }
                }
            }
            case InputChannelMeta icm -> {
                if (inputChannel != null) {
                    Log.wtf(TAG, "multiple input channels!");
                    Log.w(TAG, "not initializing: " + icm);
                    continue;
                }

                Log.d(TAG, "init input channel: " + icm);
                inputChannel = new InputChannel(mb, icm);
                inputChannel.openChannel();
                projectionService.setInput(inputChannel);
            }
            case SensorChannelMeta scm -> {
                if (sensorChannel != null) {    // this probably shouldn't happen
                    Log.wtf(TAG, "multiple sensor channels!");
                    Log.w(TAG, "not initializing: " + scm);
                    continue;
                }

                Log.d(TAG, "init sensor channel: " + scm);
                sensorChannel = new SensorChannel(mb, scm);
                sensorChannel.openChannel();
            }
            case null -> Log.w(TAG, "not initializing channel with unparsed metadata");
            default -> Log.e(TAG, "not initializing channel with unhandled metadata: " + channelMeta, new AssertionError());
        }

        Log.i(TAG, "done initializing channels");
    }

    @Override
    public void onProjectionStarted() {
        Log.i(TAG, "projection started");
    }

    @Override
    public void onProjectionFailed(Throwable t) {
        Log.e(TAG, "projection failed to launch, bailing");
        mb.closeConnection();
    }

    @Override
    public void onMessage(int channelId, int flags, byte[] buffer, int payloadOffset, int payloadLength) {
        if (payloadLength < COMMAND_ID_LENGTH) {
            Log.wtf(TAG, "message payload too small!", new RuntimeException());
            return;
        }

        int command = readUInt16(buffer, payloadOffset);
        switch (command) {
            case CMD_PING_REQUEST -> {
                PingRequest request = PingRequest.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                Log.d(TAG, "ping! " + request);

                if (request == null) {
                    // fallback
                    mb.sendMessage(
                            !tlsService.needsHandshake() ? encryptedParams : unencryptedParams,
                            CMD_PING_RESPONSE);
                    return;
                }

                mb.sendMessage(
                        !tlsService.needsHandshake() ? encryptedParams : unencryptedParams,
                        CMD_PING_RESPONSE, PingResponse.fromRequest(request).serialize());
            }

            case CMD_VERSION_REQUEST -> {
                Log.d(TAG, "version request: " + hexDump(buffer, payloadOffset, payloadLength));

                // TODO: rework
                if (payloadLength >= COMMAND_ID_LENGTH + 2) {
                    int major = readUInt16(buffer, COMMAND_ID_LENGTH);
                    int minor = readUInt16(buffer, COMMAND_ID_LENGTH + 2);
                    Log.v(TAG, "headunit version code: " + major + "." + minor);
                }

                // TODO: rework
                int i = 0;
                byte[] payload = new byte[8];
                i += writeUInt16(CMD_VERSION_RESPONSE, payload, i);
                i += writeUInt16(VERSION_CODE_MAJOR, payload, i);
                i += writeUInt16(VERSION_CODE_MINOR, payload, i);
                i += writeUInt16(0, payload, i);   // version code status. I assume this is for negotiation. (todo)
                mb.sendMessage(unencryptedParams, payload);
            }

            case CMD_SSL_HANDSHAKE -> {
                Log.d(TAG, "recv SSL/TLS handshake data, len = " + payloadLength);

                try {
                    tlsService.doHandshake(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH, out -> {
                        byte[] data = new byte[out.remaining()];
                        out.get(data);
                        mb.sendMessage(unencryptedParams, CMD_SSL_HANDSHAKE, data);
                    });
                } catch (SSLException e) {
                    Log.e(TAG, "exception during SSL handshake", e);
                    mb.closeConnection();
                }
            }

            case CMD_AUTH_COMPLETE -> {
                Log.i(TAG, "auth complete: " + hexDump(buffer, payloadOffset, payloadLength));

                if (tlsService.needsHandshake()) {
                    Log.wtf(TAG, "auth complete before handshake completed?");
                    mb.closeConnection();
                    return;
                }

                Log.i(TAG, "sending service discovery request");
                mb.sendMessage(encryptedParams, CMD_SERVICE_DISCOVERY_REQUEST, ServiceDiscoveryRequest.getDefault().serialize());
            }

            case CMD_SERVICE_DISCOVERY_RESPONSE -> {
                Log.d(TAG, "service discovery response");

                if (tlsService.needsHandshake()) {
                    Log.wtf(TAG, "service discovery response before handshake completed?"); // a request shouldn't have been sent yet
                    mb.closeConnection();
                    return;
                }

                if (videoChannel != null) {
                    Log.wtf(TAG, "service discovery response after video init?");
                    mb.closeConnection();
                    return;
                }

                ServiceDiscoveryResponse response = ServiceDiscoveryResponse.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                if (response == null) {
                    Log.e(TAG, "failed to parse service discovery response, bailing!");
                    mb.closeConnection();
                    return;
                }

                Log.d(TAG, "response data: " + response);
                serviceDiscoveryResponse = response;
                startProjection();

            }

            case CMD_AUDIO_FOCUS_RESPONSE -> {
                AudioFocusResponse response = AudioFocusResponse.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                Log.d(TAG, "audio focus response: " + response);
            }

            default -> {
                Log.w(TAG, "control command not handled: " + command);
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }
        }
    }
}
