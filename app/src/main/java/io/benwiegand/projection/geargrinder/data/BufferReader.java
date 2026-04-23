package io.benwiegand.projection.geargrinder.data;

import java.nio.ByteBuffer;

public interface BufferReader {

    void read(byte[] out, int outOffset, int readLength);

    void read(ByteBuffer out, int readLength);

    default void read(ByteBuffer out) {
        read(out, length());
    }

    int length();

    int index();

    default int remaining() {
        return length() - index();
    }

    default boolean hasRemaining() {
        return remaining() > 0;
    }

    default boolean initialized() {
        return length() == remaining();
    }

    void reset();

    static BufferReader from(byte[] buffer) {
        return new ByteArrayBufferReader(buffer);
    }

    static BufferReader from(byte[] buffer, int offset, int length) {
        return new ByteArrayBufferReader(buffer, offset, length);
    }

    static BufferReader from(ByteBuffer buffer) {
        return new ByteBufferReader(buffer);
    }

    static BufferReader join(BufferReader... bufferReaders) {
        if (bufferReaders.length == 0) return from(new byte[0]);
        if (bufferReaders.length == 1) return bufferReaders[0];
        return new MultiBufferReader(bufferReaders);
    }

}
