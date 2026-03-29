package io.benwiegand.projection.geargrinder.proto.data.writable;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;
import io.benwiegand.projection.geargrinder.proto.data.readable.PingRequest;

public record PingResponse(long timestamp) {

    public static PingResponse fromRequest(PingRequest request) {
        return new PingResponse(request.timestamp());
    }

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarInt(1, timestamp())
        );
    }

}
