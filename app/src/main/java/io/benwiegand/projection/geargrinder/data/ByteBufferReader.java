package io.benwiegand.projection.geargrinder.data;

import java.nio.ByteBuffer;

public final class ByteBufferReader implements BufferReader {
    private final ByteBuffer buffer;
    private final int offset;
    private final int length;

    ByteBufferReader(ByteBuffer buffer) {
        this.buffer = buffer;
        offset = buffer.position();
        length = buffer.remaining();
    }

    private void assertBufferStateValid() {
        assert buffer.position() >= offset;
        assert buffer.position() <= offset + length;
        assert buffer.limit() > offset;
        assert buffer.limit() <= offset + length;
        assert buffer.limit() >= buffer.position();
    }

    private void resetLimit() {
        buffer.limit(offset + length);
    }

    @Override
    public void read(byte[] out, int outOffset, int readLength) {
        buffer.get(out, outOffset, readLength);
    }

    @Override
    public void read(ByteBuffer out, int readLength) {
        assert readLength > 0;
        assertBufferStateValid();

        try {
            buffer.limit(buffer.position() + readLength);
            assert buffer.remaining() == readLength;
            out.put(buffer);
        } finally {
            resetLimit();
         }
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public int index() {
        return buffer.position() - offset;
    }

    @Override
    public void reset() {
        buffer.position(offset);
        resetLimit();
    }
}
