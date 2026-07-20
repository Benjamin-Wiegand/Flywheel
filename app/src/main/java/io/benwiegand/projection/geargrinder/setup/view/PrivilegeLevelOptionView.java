package io.benwiegand.projection.geargrinder.setup.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatRadioButton;

import io.benwiegand.projection.geargrinder.R;

public class PrivilegeLevelOptionView extends AppCompatRadioButton {

    public PrivilegeLevelOptionView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public PrivilegeLevelOptionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public PrivilegeLevelOptionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        setBackgroundResource(R.drawable.setup_privilege_level_background);
        setButtonDrawable(null);

        try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PrivilegeLevelOptionView, defStyleAttr, 0)) {
            Drawable icon = a.getDrawable(R.styleable.PrivilegeLevelOptionView_icon);
            String text = a.getString(R.styleable.PrivilegeLevelOptionView_text);

            setText(text);

            setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
        }
    }
}
