package io.benwiegand.projection.geargrinder.settings.ui.preference;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.openLink;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import io.benwiegand.projection.geargrinder.R;

public class LinkPreference extends Preference {

    private String link;

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.LinkPreference, defStyleAttr, defStyleRes)) {
            link = a.getString(R.styleable.LinkPreference_link);
        }
    }

    public LinkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    public LinkPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    public LinkPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public LinkPreference(@NonNull Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    @Override
    protected void onClick() {
        assert getPreferenceManager() != null;
        getPreferenceManager().showDialog(this);
    }

    public void open(Activity activity) {
        openLink(activity, link);
    }
}
