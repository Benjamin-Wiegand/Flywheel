package io.benwiegand.projection.geargrinder.projection.ui.indicator;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;

import androidx.appcompat.content.res.AppCompatResources;

import io.benwiegand.projection.geargrinder.R;

public class CellIndicatorIcon extends DrawableWrapper {
    public static int CELL_BARS = 4;

    private final Drawable cellLevels;
    private final Drawable limitedIndicator;
    private final Drawable noDataIndicator;

    public CellIndicatorIcon(Context context) {
        super(AppCompatResources.getDrawable(context, R.drawable.cellular_indicator));
        assert getDrawable() != null;

        LayerDrawable root = (LayerDrawable) getDrawable();
        cellLevels = root.findDrawableByLayerId(R.id.cellular_levels);
        limitedIndicator = root.findDrawableByLayerId(R.id.cellular_limited);
        noDataIndicator = root.findDrawableByLayerId(R.id.cellular_no_data);
    }

    public void update(int bars, boolean noData, boolean limited) {
        assert bars >= 0 && bars <= CELL_BARS;
        bars = Math.clamp(bars, 0, CELL_BARS);

        cellLevels.setLevel(bars);

        if (limited) {
            noDataIndicator.setAlpha(0);
            limitedIndicator.setAlpha(255);
        } else if (noData) {
            noDataIndicator.setAlpha(255);
            limitedIndicator.setAlpha(0);
        } else {
            noDataIndicator.setAlpha(0);
            limitedIndicator.setAlpha(0);
        }
    }
}
