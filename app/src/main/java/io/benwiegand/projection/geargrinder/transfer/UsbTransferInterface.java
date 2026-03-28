package io.benwiegand.projection.geargrinder.transfer;

import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.benwiegand.projection.geargrinder.message.AAFrame;

// uses USB bulk transfers to separate AA messages
// other implementations may need to leverage the 16-bit payload size in each message
public class UsbTransferInterface implements AATransferInterface {
    private final ParcelFileDescriptor pfd;
    private final InputStream is;
    private final OutputStream os;
    private final int maxTransferSize;
    private final byte[] readCache;
    private int readCacheLength = 0;

    private boolean alive = true;

    public UsbTransferInterface(ParcelFileDescriptor pfd, InputStream is, OutputStream os, int maxTransferSize) {
        this.pfd = pfd;
        this.is = is;
        this.os = os;
        this.maxTransferSize = maxTransferSize;
        readCache = new byte[maxTransferSize];
    }

    @Override
    public boolean alive() {
        return alive;
    }

    @Override
    public void sendFrame(byte[] buffer, int offset, int length) throws IOException {
        assert length <= maxTransferSize;
        try {
            os.write(buffer, offset, length);
        } catch (IOException e) {
            alive = false;
            throw e;
        }
    }

    private void readTransfer() throws IOException {
        assert readCacheLength <= 0;
        int len = is.read(readCache);
        if (len < 0) throw new IOException("stream closed (" + len + ")");

        readCacheLength = len;
    }

    @Override
    public int readFrame(byte[] buffer) throws IOException {
        try {
            if (readCacheLength <= 0) readTransfer();
            AAFrame frame = new AAFrame(readCache);
            int len = frame.getLength();

            if (len > readCacheLength)
                throw new IOException("frame length out of bounds");

            System.arraycopy(frame.getBuffer(), 0, buffer, 0, len);

            if (frame.getLength() == readCacheLength) {
                readCacheLength = 0;
                return frame.getLength();
            }

            System.arraycopy(readCache, len, readCache, 0, readCacheLength - len);
            readCacheLength -= len;

            return len;
        } catch (IOException e) {
            alive = false;
            throw e;
        }

    }

    public void close() throws IOException {
        try {
            pfd.close();
        } finally {
            alive = false;
        }
    }
}
