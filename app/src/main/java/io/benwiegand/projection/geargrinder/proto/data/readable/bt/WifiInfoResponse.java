package io.benwiegand.projection.geargrinder.proto.data.readable.bt;

import android.util.Base64;
import android.util.Log;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record WifiInfoResponse(
        String essid,
        String psk,
        String bssid,
        int securityFlags,
        int type
) implements Serializable {
    private static final String TAG = WifiInfoResponse.class.getSimpleName();

    public static final int FLAG_WIFI_SECURITY_OPEN = 1;
    public static final int FLAG_WIFI_SECURITY_WEP = 2;
    public static final int FLAG_WIFI_SECURITY_WPA = 4;
    public static final int FLAG_WIFI_SECURITY_WPA2 = 8;
    public static final int FLAG_WIFI_SECURITY_ENTERPRISE = 16;

    public boolean supportsWpa2() {
        return (securityFlags & FLAG_WIFI_SECURITY_WPA2) != 0;
    }

    public static WifiInfoResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);
            return new WifiInfoResponse(
                    ProtoParser.getSingleString(buffer, fields.get(1)),
                    ProtoParser.getSingleString(buffer, fields.get(2)),
                    ProtoParser.getSingleString(buffer, fields.get(3)),
                    ProtoParser.getSingleInteger32(buffer, fields.get(4), 0),
                    ProtoParser.getSingleInteger32(buffer, fields.get(5), 0)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse WifiInfoResponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
