package io.benwiegand.projection.geargrinder.projection.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;


import java.io.IOException;
import java.nio.ByteBuffer;

import io.benwiegand.projection.geargrinder.data.BufferReader;

public class VideoEncoder {
    private static final String TAG = VideoEncoder.class.getSimpleName();

    private static final int BITRATE_MODE_AUTO = -1;

    /**
     * minimum number of duplicate frames to send after video output stops changing
     */
    private static final int MIN_DUPLICATE_FRAMES = 60;

    private static final boolean LOG_FRAME_SIZE_DEBUG = true;

    private static final int I_FRAME_INTERVAL = 5;


    private MediaCodec encoder = null;
    private Surface hardwareSurface = null;
    private FrameCopier frameCopier = null;

    private final int width;
    private final int height;
    private final int maxFrameRate;
    private final int bitrateMode;

    private int bitrate;

    private int lastFrameNumber = -1;
    private int duplicateFrames = 0;

    private final MediaCodec.BufferInfo bufferInfo;


    public VideoEncoder(int width, int height, int maxFrameRate, int bitrateMode, int bitrate) {
        this.width = width;
        this.height = height;
        this.maxFrameRate = maxFrameRate;
        this.bitrateMode = bitrateMode;
        this.bitrate = bitrate;

        bufferInfo = new MediaCodec.BufferInfo();
    }

    public void init() throws IOException {

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
//        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height);   // TODO: figure out how to determine support for this
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
        if (bitrateMode != BITRATE_MODE_AUTO)
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode);
        format.setInteger(MediaFormat.KEY_LATENCY, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            format.setInteger(MediaFormat.KEY_VIDEO_QP_P_MIN, 20);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_P_MAX, 50);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_B_MIN, 20);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_B_MAX, 50);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_I_MIN, 20);
            format.setInteger(MediaFormat.KEY_VIDEO_QP_I_MAX, 50);
        }

        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo ci : codecList.getCodecInfos()) {
            Log.i(TAG, "codec: " + ci.getName());
        }

        String codecName = codecList.findEncoderForFormat(format);
        if (bitrateMode == BITRATE_MODE_AUTO) {
            if (codecName == null) {
                Log.w(TAG, "trying vbr bitrate mode");
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
                codecName = codecList.findEncoderForFormat(format);
            }
            if (codecName == null) {
                Log.w(TAG, "trying cbr bitrate mode");
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                codecName = codecList.findEncoderForFormat(format);
            }
            if (codecName == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.w(TAG, "trying cbr fd bitrate mode");
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD);
                codecName = codecList.findEncoderForFormat(format);
            }
            if (codecName == null) {
                Log.w(TAG, "trying cq bitrate mode");
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
                codecName = codecList.findEncoderForFormat(format);
            }
        }

        if (codecName == null) {
            Log.e(TAG, "couldn't find encoder");
            throw new RuntimeException("failed to find a suitable encoder");
        }

//        String codecName = "c2.android.avc.encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "OMX.Exynos.AVC.Encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "OMX.MTK.VIDEO.ENCODER.AVC";
//        Log.i(TAG, "forcing encoder: " + codecName);

//        String codecName = "c2.mtk.avc.encoder";
//        Log.i(TAG, "forcing encoder: " + codecName);

        Log.i(TAG, "found encoder: " + codecName);
        encoder = MediaCodec.createByCodecName(codecName);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        hardwareSurface = encoder.createInputSurface();
        encoder.start();

        try {

            frameCopier = new OpenGLFrameCopier(width, height, hardwareSurface);
            try {
                frameCopier.init();
            } catch (Throwable t) {
                frameCopier.destroy();
                throw t;
            }

        } catch (Throwable t) {
            Log.e(TAG, "failed to init OpenGLFrameCopier", t);

            Log.w(TAG, "falling back to FramePassthrough!");
            frameCopier = new FramePassthrough(hardwareSurface);
        }
    }

    public void destroy() {
        if (encoder != null) {
            encoder.stop();
            encoder.release();
        }

        if (frameCopier != null) {
            frameCopier.destroy();
        }
    }

    public Surface getInputSurface() {
        return frameCopier.getInputSurface();
    }

    private void updateBitrate(int bitrate, boolean requestSync) {
        if (this.bitrate == bitrate) return;

        Bundle params = new Bundle();

        Log.i(TAG, "updating bitrate: " + this.bitrate + " -> " + bitrate);
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);

        if (requestSync) {
            Log.v(TAG, "requesting I frame");
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        }

        encoder.setParameters(params);
        this.bitrate = bitrate;
    }

    public enum FrameError {
        NO_ERROR,
        NO_FRAME,       // nothing to output
        TRY_AGAIN,      // something changed, next frame should probably work
        FAILURE,        // something unexpected happened
        END_OF_STREAM,
    }

    public static class FrameResult {
        public int length = 0;
        public long timestamp = 0;
        public FrameError error = FrameError.NO_ERROR;
        public int bufferIndex = -1;
    }

    public BufferReader getFrame(FrameResult result, long timeoutUs) throws InterruptedException {
        if (frameCopier.nextFrameNumber() == lastFrameNumber) {
            if (duplicateFrames >= MIN_DUPLICATE_FRAMES) {
                result.error = FrameError.NO_FRAME;
                return null;
            }

            duplicateFrames++;
        } else {
            duplicateFrames = 0;
        }

        lastFrameNumber = frameCopier.copyFrame();
        int index = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
        result.bufferIndex = index;
        if (index < 0) {
            switch (index) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.w(TAG, "error: INFO_OUTPUT_FORMAT_CHANGED");
                    result.error = FrameError.TRY_AGAIN;
                }
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    Log.w(TAG, "error: INFO_OUTPUT_BUFFERS_CHANGED");
                    result.error = FrameError.TRY_AGAIN;
                }
                case MediaCodec.INFO_TRY_AGAIN_LATER ->
                    // just means there's no frame yet
                    result.error = FrameError.NO_FRAME;
                default -> {
                    Log.e(TAG, "unexpected error: " + index);
                    result.error = FrameError.FAILURE;
                }
            }
            return null;
        }

        ByteBuffer encoded = encoder.getOutputBuffer(index);
        if (encoded == null) {
            Log.wtf(TAG, "got null output buffer"); // this shouldn't happen
            result.error = FrameError.FAILURE;
            return null;
        }

        try {
            boolean isFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0;
            boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
            boolean isEOS = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            
            if (isKeyFrame && LOG_FRAME_SIZE_DEBUG) {
                Log.v(TAG, "I frame size: " + bufferInfo.size);
            }

            if (isEOS) {
                Log.i(TAG, "end of stream");
                result.error = FrameError.END_OF_STREAM;
                return null;
            } else if (bufferInfo.size == 0) {    // this isn't supposed to happen
                Log.e(TAG, "buffer size is 0, but not end of stream");
                result.error = FrameError.FAILURE;
                return null;
            }

            encoded.position(bufferInfo.offset);
            encoded.limit(bufferInfo.offset + bufferInfo.size);

            if (isFrame) result.timestamp = bufferInfo.presentationTimeUs;
            result.length = bufferInfo.size;
            result.error = FrameError.NO_ERROR;
            return BufferReader.from(encoded);
        } finally {
            if (result.error != FrameError.NO_ERROR)
                encoder.releaseOutputBuffer(index, false);
        }
    }

    public void releaseOutputBuffer(int index) {
        encoder.releaseOutputBuffer(index, false);
    }
}
