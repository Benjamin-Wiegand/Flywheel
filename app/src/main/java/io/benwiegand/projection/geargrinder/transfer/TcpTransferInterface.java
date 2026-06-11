package io.benwiegand.projection.geargrinder.transfer;

import static io.benwiegand.projection.geargrinder.message.AAFrame.EXTENDED_HEADER_LENGTH;
import static io.benwiegand.projection.geargrinder.message.AAFrame.HEADER_LENGTH;
import static io.benwiegand.projection.geargrinder.util.IOUtil.readAll;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import io.benwiegand.projection.geargrinder.message.AAFrame;

public class TcpTransferInterface implements AATransferInterface {
    private final Socket socket;
    private final InputStream is;
    private final OutputStream os;


    public TcpTransferInterface(Socket socket) throws IOException {
        this.socket = socket;
        this.is = socket.getInputStream();
        this.os = socket.getOutputStream();
    }

    @Override
    public boolean alive() {
        return !socket.isClosed();
    }

    @Override
    public void sendFrame(byte[] buffer, int offset, int length) throws IOException {
        os.write(buffer, offset, length);
    }

    @Override
    public int readFrame(byte[] buffer) throws IOException {
        AAFrame frame = new AAFrame(buffer);

        readAll(is, frame.getBuffer(), 0, HEADER_LENGTH);

        int remaining = frame.getPayloadLength();
        if (frame.isFirstInSequence())
            remaining += EXTENDED_HEADER_LENGTH - HEADER_LENGTH;

        readAll(is, frame.getBuffer(), HEADER_LENGTH, remaining);

        return HEADER_LENGTH + remaining;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
