package io.benwiegand.projection.geargrinder.projection.ui;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import io.benwiegand.projection.geargrinder.projection.ui.indicator.WifiIndicatorIcon;

public class WifiIndicator {
    private static final String TAG = WifiIndicator.class.getSimpleName();

    private final WifiManager wifiManager;

    private final ImageView view;

    private final WifiIndicatorIcon indicatorIcon;

    private boolean showing = false;
    private boolean connected = false;
    private int rssi = -1;
    private boolean limited = false;

    private boolean pendingUpdate = false;


    public WifiIndicator(ImageView view) {
        this.view = view;
        Context context = view.getContext();
        wifiManager = context.getSystemService(WifiManager.class);

        indicatorIcon = new WifiIndicatorIcon(context);
        view.setImageDrawable(indicatorIcon);
        view.setVisibility(View.GONE);
    }

    private int getBars() {
        if (!connected) return 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int level = wifiManager.calculateSignalLevel(rssi);
            if (WifiIndicatorIcon.WIFI_BARS == wifiManager.getMaxSignalLevel()) return level;
            return (int) (WifiIndicatorIcon.WIFI_BARS * ((float) level / wifiManager.getMaxSignalLevel()));
        } else {
            // noinspection deprecation
            return WifiManager.calculateSignalLevel(rssi, WifiIndicatorIcon.WIFI_BARS);
        }
    }

    public void update() {
        if (!pendingUpdate) return;
        pendingUpdate = false;
        Log.d(TAG, "wifi signal updated: " + this);

        view.setVisibility(showing ? View.VISIBLE : View.GONE);

        if (!showing) return;

        if (!connected) {
            indicatorIcon.setBars(0);
            return;
        }

        if (limited) {
            indicatorIcon.setLimited();
            return;
        }

        indicatorIcon.setBars(getBars());
    }

    public WifiIndicator setShowing(boolean showing) {
        pendingUpdate |= this.showing != showing;
        this.showing = showing;
        return this;
    }

    public WifiIndicator setConnected(boolean connected) {
        pendingUpdate |= this.connected != connected;
        this.connected = connected;
        return this;
    }

    public WifiIndicator setRssi(int rssi) {
        pendingUpdate |= this.rssi != rssi;
        this.rssi = rssi;
        return this;
    }

    public WifiIndicator setLimited(boolean limited) {
        pendingUpdate |= this.limited != limited;
        this.limited = limited;
        return this;
    }

    @Override
    public String toString() {
        return "WifiIndicator{" +
                "limited=" + limited +
                ", rssi=" + rssi +
                ", connected=" + connected +
                ", showing=" + showing +
                '}';
    }
}
