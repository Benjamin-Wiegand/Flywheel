package io.benwiegand.projection.geargrinder.data;

import java.nio.ByteBuffer;

public final class ByteArrayBufferReader implements BufferReader {
    private final byte[] buffer;
    private final int offset;
    private final int length;
    private int index = 0;

    ByteArrayBufferReader(byte[] buffer, int offset, int length) {
        assert offset >= 0 && length >= 0;
        assert buffer.length >= offset + length;
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    ByteArrayBufferReader(byte[] buffer) {
        this(buffer, 0, buffer.length);
    }

    @Override
    public void read(byte[] out, int outOffset, int readLength) {
        assert outOffset >= 0 && readLength >= 0;
        assert out.length >= outOffset + readLength;
        assert remaining() >= readLength;
        System.arraycopy(buffer, offset + index, out, outOffset, readLength);
        index += readLength;
    }

    @Override
    public void read(ByteBuffer out, int readLength) {
        out.put(buffer, offset + index, readLength);
        index += readLength;
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public void reset() {
        index = 0;
    }
}
