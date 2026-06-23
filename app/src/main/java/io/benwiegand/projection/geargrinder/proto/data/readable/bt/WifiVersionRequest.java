package io.benwiegand.projection.geargrinder.proto.data.readable.bt;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record WifiVersionRequest(
        int majorVersion,
        int minorVersion,
        int[] wifiChannels
        // field 4 is device info?
) {
    private static final String TAG = WifiVersionRequest.class.getSimpleName();

    public static WifiVersionRequest parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new WifiVersionRequest(
                    ProtoParser.getSingleInteger32(buffer, fields.get(1), 0),
                    ProtoParser.getSingleInteger32(buffer, fields.get(2), 0),
                    ProtoParser.getUnsignedInteger32Array(buffer, fields.get(3))
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse WifiVersionRequest: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
