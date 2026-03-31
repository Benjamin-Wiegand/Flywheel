package io.benwiegand.projection.geargrinder.crypto;

import android.annotation.SuppressLint;
import android.util.Log;

import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.X509TrustManager;

@SuppressLint("CustomX509TrustManager") // intentionally insecure
public class LGTMTrustManager implements X509TrustManager {
    private static final String TAG = LGTMTrustManager.class.getSimpleName();

    private void logChain(X509Certificate[] chain) {
        try {
            Log.i(TAG, "chain: " + Arrays.deepToString(chain));
        } catch (Throwable t) {
            Log.e(TAG, "explosions while logging cert chain", t);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        Log.w(TAG, "skipping certificate validation");
        logChain(chain);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        Log.w(TAG, "skipping certificate validation");
        logChain(chain);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
