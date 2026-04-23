package io.benwiegand.projection.geargrinder.data;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiBufferReader implements BufferReader {
    private final BufferReader[] bufferReaders;
    private final Queue<BufferReader> bufferReaderQueue;
    private int index = 0;

    private interface BufferWriter {
        void write(BufferReader reader, int length);
    }

    MultiBufferReader(BufferReader... bufferReaders) {
        this.bufferReaders = bufferReaders;
        bufferReaderQueue = new ArrayDeque<>(bufferReaders.length);

        resetQueue();
    }

    private void resetQueue() {
        bufferReaderQueue.clear();
        for (BufferReader reader : bufferReaders) {
            assert reader.initialized();
            bufferReaderQueue.add(reader);
        }
    }

    private void readInternal(int readLength, BufferWriter writer) {
        assert remaining() >= readLength;
        int remaining = readLength;

        while (remaining > 0) {
            BufferReader currentReader = bufferReaderQueue.peek();
            if (currentReader == null) throw new ArrayIndexOutOfBoundsException(index);
            if (!currentReader.hasRemaining()) {
                bufferReaderQueue.poll();
                continue;
            }

            int length = Math.min(remaining, currentReader.length());
            writer.write(currentReader, length);

            index += length;
            remaining -= length;
        }

        assert remaining == 0;
    }

    @Override
    public void read(byte[] out, int outOffset, int readLength) {
        assert outOffset >= 0 && readLength >= 0;
        assert out.length >= outOffset + readLength;
        AtomicInteger offset = new AtomicInteger(outOffset);
        readInternal(readLength, (reader, length) ->
                reader.read(out, offset.getAndAdd(length), length));
    }

    @Override
    public void read(ByteBuffer out, int readLength) {
        assert readLength >= 0;
        readInternal(readLength, (reader, length) ->
                reader.read(out, length));
    }

    @Override
    public int length() {
        return Arrays.stream(bufferReaders)
                .map(BufferReader::length)
                .reduce(Integer::sum)
                .orElse(0);
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public void reset() {
        for (BufferReader reader : bufferReaders) reader.reset();
        resetQueue();
        index = 0;
    }
}
