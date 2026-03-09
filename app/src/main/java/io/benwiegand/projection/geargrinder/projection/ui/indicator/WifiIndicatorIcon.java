package io.benwiegand.projection.geargrinder.projection.ui.indicator;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;

import androidx.appcompat.content.res.AppCompatResources;

import io.benwiegand.projection.geargrinder.R;

public class WifiIndicatorIcon extends DrawableWrapper {
    public static int WIFI_BARS = 4;

    private final Drawable wifiLevels;
    private final Drawable limitedIndicator;

    public WifiIndicatorIcon(Context context) {
        super(AppCompatResources.getDrawable(context, R.drawable.wifi_indicator));
        assert getDrawable() != null;

        LayerDrawable root = (LayerDrawable) getDrawable();
        wifiLevels = root.findDrawableByLayerId(R.id.wifi_levels);
        limitedIndicator = root.findDrawableByLayerId(R.id.wifi_limited);
    }

    public void setLimited() {
        limitedIndicator.setAlpha(255);
        wifiLevels.setLevel(0);
    }

    public void setBars(int bars) {
        assert bars >= 0 && bars <= WIFI_BARS;
        bars = Math.clamp(bars, 0, WIFI_BARS);

        limitedIndicator.setAlpha(0);
        wifiLevels.setLevel(bars);
    }
}
