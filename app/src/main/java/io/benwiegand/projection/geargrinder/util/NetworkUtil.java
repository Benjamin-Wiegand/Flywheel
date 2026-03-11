package io.benwiegand.projection.geargrinder.util;

import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.R;

public class NetworkUtil {
    private static final String TAG = NetworkUtil.class.getSimpleName();

    public static final boolean SUPPORTS_MULTI_SIM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;

    // not in the sdk, but that might be because it isn't expected for a phone
    private static final int TELEPHONY_NETWORK_TYPE_NB_IOT_NTN = 21;

    public static int findDefaultCellularSubscriptionId() {
        int subscriptionId = SubscriptionManager.getDefaultSubscriptionId();

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            subscriptionId = SubscriptionManager.getDefaultVoiceSubscriptionId();

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId();

        return subscriptionId;
    }

    public static int getCellularModemCount(SubscriptionManager subscriptionManager, TelephonyManager telephonyManager) {
        if (!SUPPORTS_MULTI_SIM) return 1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return telephonyManager.getSupportedModemCount();

        // potential overestimate of the actual count, but that should be fine
        return subscriptionManager.getActiveSubscriptionInfoCountMax();
    }

    public static int getCellularSubscriptionIdForSlot(SubscriptionManager subscriptionManager, int slotIndex) {
        if (!SUPPORTS_MULTI_SIM) {
            if (slotIndex != 0) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            return findDefaultCellularSubscriptionId();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return SubscriptionManager.getSubscriptionId(slotIndex);

        int[] subscriptions = subscriptionManager.getSubscriptionIds(slotIndex);
        if (subscriptions == null) return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        return subscriptions[0];
    }

    public static boolean isNetworkLimited(NetworkCapabilities networkCapabilities) {
        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
        return (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) ||
                (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));
    }

    public static int getCurrentCellularDataSubscriptionId() {
        int subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            subscriptionId = SubscriptionManager.getActiveDataSubscriptionId();

        if (subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
        return subscriptionId;
    }

    @StringRes
    public static int getCellularDataNetworkBadgeText(int networkType, int overrideNetworkType) {
        int overrideBadge = switch (overrideNetworkType) {
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> R.string.cellular_network_badge_lte_plus;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> R.string.cellular_network_badge_5ge;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> R.string.cellular_network_badge_5g;
            case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> R.string.cellular_network_badge_5g_plus;
            default -> -1;
        };
        if (overrideBadge != -1) return overrideBadge;

        return switch (networkType) {
            case TelephonyManager.NETWORK_TYPE_NR -> R.string.cellular_network_badge_5g;

            case TelephonyManager.NETWORK_TYPE_LTE -> R.string.cellular_network_badge_lte;

            case TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSPA,
                 TelephonyManager.NETWORK_TYPE_HSUPA -> R.string.cellular_network_badge_h;

            case TelephonyManager.NETWORK_TYPE_HSPAP -> R.string.cellular_network_badge_h_plus;

            case TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_EVDO_A,
                 TelephonyManager.NETWORK_TYPE_TD_SCDMA, TelephonyManager.NETWORK_TYPE_UMTS,
                 TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EVDO_0 -> R.string.cellular_network_badge_3g;

            case TelephonyManager.NETWORK_TYPE_1xRTT,
                 TelephonyManager.NETWORK_TYPE_CDMA -> R.string.cellular_network_badge_1x;

            case TelephonyManager.NETWORK_TYPE_EDGE -> R.string.cellular_network_badge_e;

            case TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_GSM,
                 TELEPHONY_NETWORK_TYPE_NB_IOT_NTN -> R.string.cellular_network_badge_g;

            // no icon for these
            case TelephonyManager.NETWORK_TYPE_IWLAN -> R.string.cellular_network_badge_none;
            case TelephonyManager.NETWORK_TYPE_IDEN -> R.string.cellular_network_badge_none;
            case TelephonyManager.NETWORK_TYPE_UNKNOWN -> R.string.cellular_network_badge_none;
            default -> {
                Log.wtf(TAG, "no badge for unhandled NETWORK_TYPE: " + networkType, new RuntimeException());
                yield R.string.cellular_network_badge_unknown;
            }
        };
    }
}
