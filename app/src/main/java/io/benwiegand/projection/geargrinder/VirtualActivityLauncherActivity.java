package io.benwiegand.projection.geargrinder;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import io.benwiegand.projection.libprivd.ipc.IPCConstants;

public class VirtualActivityLauncherActivity extends AppCompatActivity {
    private static final String TAG = VirtualActivityLauncherActivity.class.getSimpleName();

    private static final String INTENT_EXTRA_ACTIVITY_COMPONENT = IPCConstants.VIRTUAL_ACTIVITY_LAUNCHER_INTENT_EXTRA_ACTIVITY;

    private static final long LAUNCH_TIMEOUT = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ComponentName componentName;

    private Object launchToken = new Object();

    private boolean firstLaunch = true;
    private boolean activityLost = false;
    private boolean wasPaused = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_activity_launcher);

        Intent intent = getIntent();
        if (intent == null) {
            Log.wtf(TAG, "no launch intent");
            finish();
            return;
        }

        String component = intent.getStringExtra(INTENT_EXTRA_ACTIVITY_COMPONENT);
        if (component == null) {
            Log.wtf(TAG, "missing activity intent extra");
            finish();
            return;
        }

        componentName = ComponentName.unflattenFromString(component);

        View relaunchButton = findViewById(R.id.relaunch_button);
        relaunchButton.setOnClickListener(v -> tryLaunch());

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.v(TAG, "back button consumed");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // this is preferable since it shows a cohesive frame on the screen that accurately indicates what's currently happening.
            // without this, the first frame the user sees will be blank, broken, or outdated.
            View root = findViewById(R.id.root);
            root.getViewTreeObserver().registerFrameCommitCallback(new Runnable() {
                @Override
                public void run() {
                    root.getViewTreeObserver().unregisterFrameCommitCallback(this);
                    doFirstLaunch();
                }
            });

            handler.postDelayed(() -> {
                if (!firstLaunch) return;
                Log.wtf(TAG, "frame commit callback not called after " + LAUNCH_TIMEOUT + " ms! forcing first launch");
                doFirstLaunch();
            }, LAUNCH_TIMEOUT);
        } else if (firstLaunch) {
            doFirstLaunch();
        }


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.w(TAG, "onDestroy()");
    }

    private void updateStatus(@StringRes int text) {
        TextView statusText = findViewById(R.id.launch_status_text);
        View relaunchButton = findViewById(R.id.relaunch_button);
        ProgressBar launchingIndicator = findViewById(R.id.launching_indicator);

        statusText.setText(text);

        if (text == R.string.virtual_activity_launcher_status_launching) {
            relaunchButton.setVisibility(View.GONE);
            launchingIndicator.setVisibility(View.VISIBLE);
        } else {
            relaunchButton.setVisibility(View.VISIBLE);
            launchingIndicator.setVisibility(View.GONE);
        }
    }

    private void doFirstLaunch() {
        if (!firstLaunch) return;
        Log.i(TAG, "first launch");
        tryLaunch();
    }

    private void tryLaunch() {
        Log.i(TAG, "launching activity: " + componentName);
        launchToken = new Object();
        firstLaunch = false;
        activityLost = false;
        wasPaused = false;
        updateStatus(R.string.virtual_activity_launcher_status_launching);
        try {
            startActivity(new Intent().setComponent(componentName));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        } catch (Throwable t) {
            Log.e(TAG, "activity launch failed", t);
            updateStatus(R.string.virtual_activity_launcher_status_failed);
            return;
        }


        Object token = launchToken;
        handler.postDelayed(() -> {
            if (launchToken != token) return;
            if (wasPaused) return;

            Log.i(TAG, "no call to onPause after " + LAUNCH_TIMEOUT + " ms");
            updateStatus(R.string.virtual_activity_launcher_status_failed);
        }, LAUNCH_TIMEOUT);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (firstLaunch) return;
        if (activityLost) return;

        Log.i(TAG, "activity lost: " + componentName);
        updateStatus(R.string.virtual_activity_launcher_status_closed);
        activityLost = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause()");
        wasPaused = true;
    }
}
