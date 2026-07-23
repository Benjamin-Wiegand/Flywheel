package io.benwiegand.projection.geargrinder;

import static androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

import static io.benwiegand.projection.geargrinder.util.UiUtil.dpToPx;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
    private static final String TAG = ScreenWakeActivity.class.getSimpleName();

    public static final String INTENT_ACTION_FINISH = "io.benwiegand.projection.geargrinder.ScreenWakeActivity.FINISH";

    // moves the info section for burn-in reduction
    private static final long MOVE_INFO_SECTION_INTERVAL = 60000;

    private static final long MAX_DOUBLE_TAP_DELAY = 500;
    private static final float MAX_DOUBLE_TAP_GROUPING_DISTANCE_DP = 48;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private float maxDoubleTapGroupingDistance;

    private boolean previouslyShowing = false;
    private Object doubleTapResetToken = new Object();
    private int tapCounter = 0;

    private float downX = 0;
    private float downY = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
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

        maxDoubleTapGroupingDistance = dpToPx(this, MAX_DOUBLE_TAP_GROUPING_DISTANCE_DP);

        onIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        Log.d(TAG, "got intent: " + intent);
        if (intent == null) return;
        if (INTENT_ACTION_FINISH.equals(intent.getAction())) {
            Log.i(TAG, "finish intent");
            finish();
            return;
        }

        Log.i(TAG, "launch intent, previouslyShowing=" + previouslyShowing);
        if (previouslyShowing) finish();
        previouslyShowing = true;
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

    private void startDoubleTapResetDelay() {
        Object token = new Object();
        doubleTapResetToken = token;
        handler.postDelayed(() -> {
            if (doubleTapResetToken != token) return;
            tapCounter = 0;
        }, MAX_DOUBLE_TAP_DELAY);
    }

    public boolean onMotionEvent(MotionEvent event) {
        // don't count multi-touch
        if (event.getPointerCount() > 1) {
            tapCounter = 0;
            return false;
        }

        // limit double tap grouping to a normal ui button size
        if (tapCounter > 0) {
            float diffX = Math.abs(downX - event.getX());
            float diffY = Math.abs(downY - event.getY());
            if (diffX > maxDoubleTapGroupingDistance || diffY > maxDoubleTapGroupingDistance) {
                tapCounter = 0;
                return false;
            }
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN -> {
                if (tapCounter == 0) {
                    downX = event.getX();
                    downY = event.getY();
                    tapCounter = 1;
                    startDoubleTapResetDelay();
                    return true;
                } else if (tapCounter == 1) {
                    tapCounter = 2;
                    return true;
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (tapCounter == 2) {
                    Log.i(TAG, "double tap");
                    finish();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return onMotionEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return onMotionEvent(event);
    }
}
