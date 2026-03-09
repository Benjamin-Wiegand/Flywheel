package io.benwiegand.projection.geargrinder.projection.ui;

import static io.benwiegand.projection.geargrinder.util.NetworkUtil.SUPPORTS_MULTI_SIM;
import static io.benwiegand.projection.geargrinder.util.NetworkUtil.getCellularDataNetworkBadgeText;
import static io.benwiegand.projection.geargrinder.util.NetworkUtil.getCurrentCellularDataSubscriptionId;
import static io.benwiegand.projection.geargrinder.util.NetworkUtil.isNetworkLimited;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TelephonyNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.projection.ui.indicator.CellIndicatorIcon;

public class CellIndicator {
    private static final String TAG = CellIndicator.class.getSimpleName();

    private static final boolean SUPPORTS_TELEPHONY_DISPLAY_INFO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    private static final boolean SUPPORTS_TELEPHONY_DISPLAY_INFO_WITHOUT_READ_PHONE_STATE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    private static final boolean USES_TELEPHONY_CALLBACK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    private final int slotIndex;
    private final int subscriptionId;
    private final boolean useTelephonyDisplayInfo;
    private final ViewGroup parent;
    private final TelephonyManager telephonyManager;
    private final ConnectivityManager connectivityManager;

    // same callback but new/legacy
    private final CellSignalListener phoneStateListener;
    private final CellSignalListenerWrapper telephonyCallback;

    private final CellIndicatorIcon indicatorIcon;
    private final TextView indicatorView;

    private boolean connected = false;
    private boolean dataConnected = false;
    private boolean limited = false;
    private int bars = 0;
    private int networkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    private int overrideNetworkType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE : 0;
    private boolean showDataIndicator = false;

    private boolean pendingUpdate = false;

    public CellIndicator(TelephonyManager defaultTelephonyManager, LayoutInflater inflater, ViewGroup parent, int slotIndex, int subscriptionId) {
        this.slotIndex = slotIndex;
        this.subscriptionId = subscriptionId;
        this.parent = parent;

        if (isStub()) {
            useTelephonyDisplayInfo = false;
            telephonyManager = null;
            connectivityManager = null;
            phoneStateListener = null;
            telephonyCallback = null;
            indicatorIcon = null;
            indicatorView = null;
            return;
        }

        Context context = parent.getContext();
        Handler handler = new Handler(Looper.getMainLooper());
        telephonyManager = defaultTelephonyManager.createForSubscriptionId(subscriptionId);
        connectivityManager = context.getSystemService(ConnectivityManager.class);

        // views
        indicatorIcon = new CellIndicatorIcon(context);
        indicatorView = (TextView) inflater.inflate(R.layout.layout_cell_indicator, parent, false);
        indicatorView.setCompoundDrawablesWithIntrinsicBounds(null, null, indicatorIcon, null);
        parent.addView(indicatorView);

        // network callback
        if (!SUPPORTS_MULTI_SIM || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

            if (SUPPORTS_MULTI_SIM) {
                networkRequestBuilder.setNetworkSpecifier(new TelephonyNetworkSpecifier.Builder()
                        .setSubscriptionId(subscriptionId)
                        .build());
            }

            connectivityManager.registerNetworkCallback(networkRequestBuilder.build(), cellNetworkCallback, handler);
        } else {
            // for android 10: can't specify a subscription, just mirror whatever the status of the default data network is
            Log.w(TAG, "mirroring default network state for modem " + slotIndex);
            connectivityManager.registerDefaultNetworkCallback(cellNetworkCallback, handler);
        }

        // signal callback
        useTelephonyDisplayInfo = SUPPORTS_TELEPHONY_DISPLAY_INFO &&
                (SUPPORTS_TELEPHONY_DISPLAY_INFO_WITHOUT_READ_PHONE_STATE || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED);

        phoneStateListener = new CellSignalListener();
        if (USES_TELEPHONY_CALLBACK) {
            telephonyCallback = new CellSignalListenerWrapper(phoneStateListener);
            telephonyManager.registerTelephonyCallback(handler::post, telephonyCallback);
        } else {
            // noinspection deprecation
            int events = PhoneStateListener.LISTEN_SIGNAL_STRENGTHS | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE | PhoneStateListener.LISTEN_SERVICE_STATE;
            if (useTelephonyDisplayInfo) events |= PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED;
            telephonyCallback = null;
            telephonyManager.listen(phoneStateListener, events);
        }
    }

    public void destroy() {
        if (isStub()) return;   // nothing to destroy

        if (USES_TELEPHONY_CALLBACK) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback);
        } else {
            // noinspection deprecation
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        connectivityManager.unregisterNetworkCallback(cellNetworkCallback);

        parent.removeView(indicatorView);
    }

    public boolean isStub() {
        return subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void update() {
        if (isStub()) return;
        if (!pendingUpdate) return;
        pendingUpdate = false;
        Log.i(TAG, "cell signal updated for modem " + slotIndex + ": " + this);

        if (!connected) {
            indicatorIcon.update(0, true, limited);
            return;
        }

        boolean isDataSubscription = getCurrentCellularDataSubscriptionId() == subscriptionId;

        indicatorIcon.update(bars, !dataConnected && isDataSubscription, limited);

        if (dataConnected && isDataSubscription && showDataIndicator) {
            indicatorView.setText(getCellularDataNetworkBadgeText(networkType, overrideNetworkType));
        } else {
            indicatorView.setText(R.string.cellular_network_badge_none);
        }
    }

    public CellIndicator setConnected(boolean connected) {
        pendingUpdate |= this.connected != connected;
        this.connected = connected;
        return this;
    }

    public CellIndicator setDataConnected(boolean dataConnected) {
        pendingUpdate |= this.dataConnected != dataConnected;
        this.dataConnected = dataConnected;
        return this;
    }

    public CellIndicator setLimited(boolean limited) {
        pendingUpdate |= this.limited != limited;
        this.limited = limited;
        return this;
    }

    public CellIndicator setBars(int bars) {
        pendingUpdate |= this.bars != bars;
        this.bars = bars;
        return this;
    }

    public CellIndicator setNetworkType(int networkType) {
        pendingUpdate |= this.networkType != networkType;
        this.networkType = networkType;
        return this;
    }

    public CellIndicator setOverrideNetworkType(int overrideNetworkType) {
        pendingUpdate |= this.overrideNetworkType != overrideNetworkType;
        this.overrideNetworkType = overrideNetworkType;
        return this;
    }

    public CellIndicator setShowDataIndicator(boolean showDataIndicator) {
        pendingUpdate |= this.showDataIndicator != showDataIndicator;
        this.showDataIndicator = showDataIndicator;
        return this;
    }

    @Override
    public String toString() {
        return "CellIndicator{" +
                "modemIndex=" + slotIndex +
                ", subscriptionId=" + subscriptionId +
                ", connected=" + connected +
                ", dataConnected=" + dataConnected +
                ", limited=" + limited +
                ", bars=" + bars +
                ", networkType=" + networkType +
                ", overrideNetworkType=" + overrideNetworkType +
                ", showDataIndicator=" + showDataIndicator +
                '}';
    }

    private final ConnectivityManager.NetworkCallback cellNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            Log.d(TAG, "modem " + slotIndex + " network available");
        }

        @Override
        public void onLost(@NonNull Network network) {
            Log.d(TAG, "modem " + slotIndex + " network lost");
            setLimited(false);
            update();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            Log.d(TAG, "modem " + slotIndex + " capabilities changed: " + networkCapabilities);
            setLimited(isNetworkLimited(networkCapabilities));
            update();
        }
    };

    private class CellSignalListener extends PhoneStateListener {

        /** @noinspection deprecation */
        public CellSignalListener() { }

        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
            if (!useTelephonyDisplayInfo) return;
            setNetworkType(telephonyDisplayInfo.getNetworkType());
            setOverrideNetworkType(telephonyDisplayInfo.getOverrideNetworkType());
            update();
        }

        @Override
        public void onDataConnectionStateChanged(int state, int networkType) {
            Log.d(TAG, "modem " + slotIndex + " new network state: " + state);
            if (!useTelephonyDisplayInfo) setNetworkType(networkType);
            setDataConnected(state == TelephonyManager.DATA_CONNECTED);
            update();
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            setBars(signalStrength.getLevel());
            update();
        }

        /** @noinspection deprecation */
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            setConnected(serviceState.getState() == ServiceState.STATE_IN_SERVICE);
            update();
        }

    }

    // adapts newer callback to legacy one
    @RequiresApi(api = Build.VERSION_CODES.S)
    private class CellSignalListenerWrapper extends TelephonyCallback implements TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DataConnectionStateListener, TelephonyCallback.ServiceStateListener, TelephonyCallback.DisplayInfoListener {
        private final CellSignalListener wrapped;

        private CellSignalListenerWrapper(CellSignalListener wrapped) {
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
            wrapped.onServiceStateChanged(serviceState);
        }

        @SuppressLint("MissingPermission")  // this only gets called on api >=31 and the permission is only required for api <=30
        @Override
        public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo telephonyDisplayInfo) {
            wrapped.onDisplayInfoChanged(telephonyDisplayInfo);
        }
    }
}
