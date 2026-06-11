package io.benwiegand.projection.geargrinder.proto.data.writable.bt;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record WifiStartResponse(int status) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarInt(3, status())    // field 3???
        );
    }

}
