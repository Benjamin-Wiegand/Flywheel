package io.benwiegand.projection.geargrinder.transfer;

import static io.benwiegand.projection.geargrinder.util.IOUtil.readAll;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.benwiegand.projection.geargrinder.bluetooth.AABTFrame;

/**
 * reads AABTFrames from a BluetoothSocket
 */
public class BluetoothTransferInterface implements AATransferInterface {
    private final BluetoothSocket socket;
    private final InputStream is;
    private final OutputStream os;


    public BluetoothTransferInterface(BluetoothSocket socket) throws IOException {
        this.socket = socket;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
    }

    @Override
    public boolean alive() {
        return socket.isConnected();
    }

    @Override
    public void sendFrame(byte[] buffer, int offset, int length) throws IOException {
        os.write(buffer, offset, length);
        os.flush();
    }

    @Override
    public int readFrame(byte[] buffer) throws IOException {
        AABTFrame frame = new AABTFrame(buffer);
        readAll(is, buffer, 0, AABTFrame.HEADER_LENGTH);
        readAll(is, buffer, AABTFrame.PAYLOAD_OFFSET, frame.getPayloadLength());
        return frame.getLength();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
