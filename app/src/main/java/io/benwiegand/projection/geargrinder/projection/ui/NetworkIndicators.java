package io.benwiegand.projection.geargrinder.projection.ui;

import static io.benwiegand.projection.geargrinder.util.NetworkUtil.getCellularModemCount;
import static io.benwiegand.projection.geargrinder.util.NetworkUtil.getCellularSubscriptionIdForSlot;
import static io.benwiegand.projection.geargrinder.util.NetworkUtil.isNetworkLimited;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.Arrays;

import io.benwiegand.projection.geargrinder.R;

public class NetworkIndicators {
    private static final String TAG = NetworkIndicators.class.getSimpleName();

    private final WifiManager wifiManager;
    private final SubscriptionManager subscriptionManager;
    private final TelephonyManager telephonyManager;
    private final ConnectivityManager connectivityManager;

    private final ViewGroup rootView;
    private final Context context;

    private final CellIndicator[] cellIndicators;

    private final WifiIndicator wifiIndicator;


    public NetworkIndicators(ViewGroup rootView) {
        this.rootView = rootView;
        context = rootView.getContext();
        wifiManager = context.getSystemService(WifiManager.class);
        subscriptionManager = context.getSystemService(SubscriptionManager.class);
        telephonyManager = context.getSystemService(TelephonyManager.class);
        connectivityManager = context.getSystemService(ConnectivityManager.class);
        Handler handler = new Handler(Looper.getMainLooper());

        wifiIndicator = new WifiIndicator(rootView.findViewById(R.id.wifi_indicator));

        int modemCount = getCellularModemCount(subscriptionManager, telephonyManager);
        Log.d(TAG, "assumed modem count: " + modemCount);
        cellIndicators = new CellIndicator[modemCount];
        Arrays.fill(cellIndicators, null);
        inflateCellIndicators();

        connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback, handler);
    }

    // TODO: should be called when sim cards are inserted/removed, although that probably is unlikely to happen
    private void inflateCellIndicators() {
        LayoutInflater inflater = LayoutInflater.from(context);

        for (CellIndicator indicator : cellIndicators) {
            if (indicator == null) continue;
            indicator.destroy();
        }

        // assuming modem count == slot count == cell indicator count
        // TODO: this assumption appears to be wrong according to the documentation despite working in most cases.
        //       should probably look in to how the framework determines the number of cell status indicators
        for (int i = 0; i < cellIndicators.length; i++) {
            int subscriptionId = getCellularSubscriptionIdForSlot(subscriptionManager, i);
            if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                Log.i(TAG, "no subscription for slot " + i);
            else {
                Log.i(TAG, "found subscription " + subscriptionId + " for slot " + i);
            }

            cellIndicators[i] = new CellIndicator(telephonyManager, inflater, rootView, i, subscriptionId);
        }

    }

    private WifiInfo getWifiInfo(NetworkCapabilities networkCapabilities) {
        WifiInfo wifiInfo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && networkCapabilities.getTransportInfo() instanceof WifiInfo info)
            wifiInfo = info;

        if (wifiInfo == null)
            wifiInfo = wifiManager.getConnectionInfo();

        return wifiInfo;
    }

    private final ConnectivityManager.NetworkCallback defaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onLost(@NonNull Network network) {
            Log.d(TAG, "onLost: " + network.getNetworkHandle());
            wifiIndicator
                    .setShowing(false)
                    .setConnected(false)
                    .update();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            Log.d(TAG, "onCapabilitiesChanged: network=" + network.getNetworkHandle() + ", capabilities=" + networkCapabilities);

            boolean isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

            // active data indicator
            for (int i = 0; i < cellIndicators.length; i++) cellIndicators[i]
                    .setShowDataIndicator(isCellular)
                    .update();

            // wifi
            wifiIndicator.setShowing(isWifi);   // match systemui status bar behavior
            if (!isWifi) {
                wifiIndicator.update();
                return;
            }

            wifiIndicator.setLimited(isNetworkLimited(networkCapabilities));

            WifiInfo wifiInfo = getWifiInfo(networkCapabilities);
            wifiIndicator.setConnected(wifiInfo != null);
            if (wifiInfo == null) {
                Log.wtf(TAG, "unable to fetch wifi info: " +
                        "\n - network = " + network.getNetworkHandle() +
                        "\n - capabilities = " + networkCapabilities);
                wifiIndicator.update();
                return;
            }

            wifiIndicator
                    .setConnected(true)
                    .setRssi(wifiInfo.getRssi())
                    .update();
        }

    };

    public void destroy() {
        connectivityManager.unregisterNetworkCallback(defaultNetworkCallback);

        for (CellIndicator cellIndicator : cellIndicators)
            cellIndicator.destroy();
    }

}
