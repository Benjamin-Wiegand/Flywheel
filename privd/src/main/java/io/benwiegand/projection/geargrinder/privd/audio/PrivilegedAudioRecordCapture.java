package io.benwiegand.projection.geargrinder.privd.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Log;

import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedAudioAttributesBuilder;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedAudioRecordBuilder;
import io.benwiegand.projection.libprivd.audio.AudioRecordCapture;

public class PrivilegedAudioRecordCapture extends AudioRecordCapture {
    private static final String TAG = PrivilegedAudioRecordCapture.class.getSimpleName();

    @SuppressLint("MissingPermission")
    private static AudioRecord createAudioRecord(Context context, AudioFormat audioFormat, int bufferSize, int audioSource) {

        AudioRecord.Builder audioRecordBuilder = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioSource(audioSource);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            audioRecordBuilder.setContext(context);

        try {
            AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder();

            new ReflectedAudioAttributesBuilder(audioAttributesBuilder)
                    .setInternalCapturePreset(audioSource)
                    .addTag(ReflectedAudioRecordBuilder.SUBMIX_FIXED_VOLUME);

            new ReflectedAudioRecordBuilder(audioRecordBuilder)
                    .setAudioAttributes(audioAttributesBuilder.build());

        } catch (Throwable t) {
            Log.e(TAG, "failed to set custom audio attributes", t);
        }

        return audioRecordBuilder.build();
    }

    @SuppressLint("MissingPermission")
    public PrivilegedAudioRecordCapture(Context context, AudioFormat audioFormat, int bufferSize, int audioSource) {
        super(createAudioRecord(context, audioFormat, bufferSize, audioSource));
    }

}
