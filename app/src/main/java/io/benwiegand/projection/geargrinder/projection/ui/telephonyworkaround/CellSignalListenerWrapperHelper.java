package io.benwiegand.projection.geargrinder.projection.ui.telephonyworkaround;

import android.os.Build;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.S)
public class CellSignalListenerWrapperHelper {
    private final CellSignalListenerWrapper wrapper;

    public CellSignalListenerWrapperHelper(PhoneStateListener listener) {
        wrapper = new CellSignalListenerWrapper(listener);
    }

    public void registerCallback(TelephonyManager telephonyManager, Handler handler) {
        telephonyManager.registerTelephonyCallback(handler::post, wrapper);
    }

    public void unregisterCallback(TelephonyManager telephonyManager) {
        telephonyManager.unregisterTelephonyCallback(wrapper);
    }

}
