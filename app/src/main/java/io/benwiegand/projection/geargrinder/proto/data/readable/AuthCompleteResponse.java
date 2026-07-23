package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record AuthCompleteResponse(
        long status
) {
    private static final String TAG = AuthCompleteResponse.class.getSimpleName();

    public static AuthCompleteResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new AuthCompleteResponse(
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), 0)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AuthCompleteResponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
