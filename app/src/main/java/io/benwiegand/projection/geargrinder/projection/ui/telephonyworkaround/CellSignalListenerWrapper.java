package io.benwiegand.projection.geargrinder.projection.ui.telephonyworkaround;

import android.annotation.SuppressLint;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.benwiegand.projection.geargrinder.projection.ui.CellIndicator;


/**
 * adapts newer {@link TelephonyCallback} to legacy {@link PhoneStateListener} for callbacks used in {@link CellIndicator}
 */
@RequiresApi(api = Build.VERSION_CODES.S)
class CellSignalListenerWrapper extends TelephonyCallback implements TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DataConnectionStateListener, TelephonyCallback.ServiceStateListener, TelephonyCallback.DisplayInfoListener {
    private final PhoneStateListener wrapped;

    CellSignalListenerWrapper(PhoneStateListener wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void onDataConnectionStateChanged(int state, int networkType) {
        wrapped.onDataConnectionStateChanged(state, networkType);
    }

    @Override
    public void onSignalStrengthsChanged(@NonNull SignalStrength signalStrength) {
        wrapped.onSignalStrengthsChanged(signalStrength);
    }

    @Override
    public void onServiceStateChanged(@NonNull ServiceState serviceState) {
        // noinspection deprecation
        wrapped.onServiceStateChanged(serviceState);
    }

    @SuppressLint("MissingPermission")  // this only gets called on api >=31 and the permission is only required for api <=30
    @Override
    public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
        wrapped.onDisplayInfoChanged(telephonyDisplayInfo);
    }
}
