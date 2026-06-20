package io.benwiegand.projection.libprivd.audio;

public interface AudioCapture {

    void begin();
    void destroy();

    void nextBuffer(AudioCaptureResult result, byte[] buffer, int offset, int length);

}
