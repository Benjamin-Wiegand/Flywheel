package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record PingRequest(
        long timestamp,
        long unknown
) {
    private static final String TAG = PingRequest.class.getSimpleName();

    public static PingRequest parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new PingRequest(
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), 0),
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), 0)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse PingRequest: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
