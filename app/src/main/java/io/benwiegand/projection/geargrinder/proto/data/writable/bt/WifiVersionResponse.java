package io.benwiegand.projection.geargrinder.proto.data.writable.bt;

import java.nio.charset.StandardCharsets;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record WifiVersionResponse(
        int versionMajor,
        int versionMinor,
        String serial,
        int status
        // field 6 unknown (list of uuids)
) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarInt(1, versionMajor()),
                new ProtoSerializer.ProtoVarInt(2, versionMinor()),
                new ProtoSerializer.ProtoVarData(3, serial.getBytes(StandardCharsets.UTF_8)),
                new ProtoSerializer.ProtoVarInt(4, status())
        );
    }

}
