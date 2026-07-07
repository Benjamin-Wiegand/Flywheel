package io.benwiegand.projection.geargrinder;

import static androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Random;

public class ScreenWakeActivity extends AppCompatActivity {

    public static final String INTENT_ACTION_FINISH = "io.benwiegand.projection.geargrinder.ScreenWakeActivity.FINISH";

    // moves the info section for burn-in reduction
    private static final long MOVE_INFO_SECTION_INTERVAL = 60000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Random random = new Random();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_wake);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true);
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.displayCutout());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                moveInfoSection();
            }
        });

        onIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        if (intent == null) return;
        if (!INTENT_ACTION_FINISH.equals(intent.getAction())) return;
        finish();
    }

    private void moveInfoSection() {
        try {
            View infoSection = findViewById(R.id.moving_info_section);
            View root = findViewById(R.id.root);

            int xMax = root.getWidth() - infoSection.getWidth() - root.getPaddingRight() - root.getPaddingLeft();
            int yMax = root.getHeight() - infoSection.getHeight() - root.getPaddingBottom() - root.getPaddingTop();

            // the text going off the screen is preferable to screen burn-in
            if (xMax <= 0) xMax = infoSection.getWidth();
            if (yMax <= 0) yMax = infoSection.getHeight();

            int x = random.nextInt(xMax);
            int y = random.nextInt(yMax);

            infoSection.setTranslationX(x);
            infoSection.setTranslationY(y);
        } finally {
            handler.postDelayed(this::moveInfoSection, MOVE_INFO_SECTION_INTERVAL);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        finish();
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        finish();
        return super.onGenericMotionEvent(event);
    }
}
