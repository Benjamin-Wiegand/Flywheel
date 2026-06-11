package io.benwiegand.projection.geargrinder.bluetooth;

import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

public class AABTFrame {
    public static final int HEADER_LENGTH = 4;

    public static final int PAYLOAD_LENGTH_OFFSET = 0;
    public static final int COMMAND_OFFSET = 2;
    public static final int PAYLOAD_OFFSET = 4;

    private final byte[] buffer;

    public AABTFrame(byte[] buffer) {
        assert buffer.length >= HEADER_LENGTH;
        this.buffer = buffer;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getLength() {
        return HEADER_LENGTH + getPayloadLength();
    }

    public int getCommand() {
        return readUInt16(buffer, COMMAND_OFFSET);
    }

    public AABTFrame setCommand(int command) {
        assert command <= 0xffff && command >= 0;
        writeUInt16(command, buffer, COMMAND_OFFSET);
        return this;
    }

    public int getPayloadLength() {
        return readUInt16(buffer, PAYLOAD_LENGTH_OFFSET);
    }

    public AABTFrame setPayloadLength(int payloadLength) {
        assert payloadLength <= 0xffff && payloadLength >= 0;
        assert buffer.length - PAYLOAD_OFFSET >= payloadLength;
        writeUInt16(payloadLength, buffer, PAYLOAD_LENGTH_OFFSET);
        return this;
    }

    public AABTFrame copyPayload(byte[] src, int offset, int length) {
        setPayloadLength(length);
        System.arraycopy(src, offset, buffer, PAYLOAD_OFFSET, length);
        return this;
    }

    public AABTFrame copyPayload(byte[] src) {
        copyPayload(src, 0, src.length);
        return this;
    }

    @Override
    public String toString() {
        return "AABTFrame{" +
                "payloadLength=" + getPayloadLength() +
                ", command=" + getCommand() +
                ", buffer=(" + buffer.length + " bytes, full header: " + hexDump(buffer, 0, HEADER_LENGTH) + ")" +
                '}';
    }
}
