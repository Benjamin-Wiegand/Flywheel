package io.benwiegand.projection.geargrinder.projection.ui.task;

import android.util.TypedValue;
import android.view.View;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;

public class FocusTracker {

    private VirtualActivity focusedActivity;

    private final int activeColor;
    private final int inactiveColor;

    public FocusTracker(VirtualActivity initialFocus) {
        focusedActivity = initialFocus;

        TypedValue typedValue = new TypedValue();
        boolean found;

        found = initialFocus.getContext().getTheme().resolveAttribute(R.attr.colorFocusIndicatorActive, typedValue, true);
        assert found;
        activeColor = typedValue.data;

        found = initialFocus.getContext().getTheme().resolveAttribute(R.attr.colorFocusIndicatorInactive, typedValue, true);
        assert found;
        inactiveColor = typedValue.data;

        View focusIndicator = focusedActivity.getFocusIndicatorView();
        focusIndicator.setVisibility(View.VISIBLE);
    }

    private void setInactive(VirtualActivity activity) {
        activity.getFocusIndicatorView().setBackgroundColor(inactiveColor);
    }

    private void setActive(VirtualActivity activity) {
        activity.getFocusIndicatorView().setBackgroundColor(activeColor);
    }

    public void takeFocus(VirtualActivity activity) {
        if (activity == focusedActivity) return;
        setInactive(focusedActivity);
        setActive(activity);
        focusedActivity = activity;
    }

    public VirtualActivity getFocus() {
        return focusedActivity;
    }

    public void updateIndicators(List<VirtualActivity> activities) {
        if (activities.isEmpty()) return;

        boolean focusFound = false;
        for (VirtualActivity activity : activities) {
            if (activity == focusedActivity) {
                setActive(activity);
                focusFound = true;
            } else {
                setInactive(activity);
            }
        }

        if (!focusFound) takeFocus(activities.get(0));
    }

}
