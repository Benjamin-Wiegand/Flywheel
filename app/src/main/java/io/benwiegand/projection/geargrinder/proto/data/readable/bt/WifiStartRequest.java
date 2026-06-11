package io.benwiegand.projection.geargrinder.proto.data.readable.bt;

import android.util.Base64;
import android.util.Log;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record WifiStartRequest(
        String ipAddress,
        int port
) implements Serializable {
    private static final String TAG = WifiStartRequest.class.getSimpleName();
    public static final int DEFAULT_PORT = 5288;

    public static WifiStartRequest parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);
            return new WifiStartRequest(
                    ProtoParser.getSingleString(buffer, fields.get(1)),
                    ProtoParser.getSingleInteger32(buffer, fields.get(2), DEFAULT_PORT)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse WifiStartRequest: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
