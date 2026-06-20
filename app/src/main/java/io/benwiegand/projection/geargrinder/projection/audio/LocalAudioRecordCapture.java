package io.benwiegand.projection.geargrinder.projection.audio;

import android.Manifest;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;
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

}
