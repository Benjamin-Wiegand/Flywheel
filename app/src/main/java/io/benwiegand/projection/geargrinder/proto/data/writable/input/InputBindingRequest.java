package io.benwiegand.projection.geargrinder.proto.data.writable.input;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record InputBindingRequest(int[] keyCodes) {

    public byte[] serialize() {
        // TODO: this is probably wrong, I think the vardata here uses varints within?
        //       but this matches the broken behavior of the service discovery parsing

        boolean singleByteCodes = true;
        for (int i = 0; i < keyCodes.length; i++) {
            if (keyCodes()[i] > 255) {
                singleByteCodes = false;
                break;
            }
        }

        if (singleByteCodes) {
            byte[] keyCodeBytes = new byte[keyCodes().length];
            for (int i = 0; i < keyCodes().length; i++)
                keyCodeBytes[i] = (byte) keyCodes()[i];

            return ProtoSerializer.serialize(
                    new ProtoSerializer.ProtoVarData(1, keyCodeBytes)
            );
        } else {
            ProtoSerializer.ProtoVarInt[] keyCodeFields = new ProtoSerializer.ProtoVarInt[keyCodes().length];
            for (int i = 0; i < keyCodes().length; i++)
                keyCodeFields[i] = new ProtoSerializer.ProtoVarInt(1, keyCodes()[i]);

            return ProtoSerializer.serialize(keyCodeFields);
        }
    }

}
