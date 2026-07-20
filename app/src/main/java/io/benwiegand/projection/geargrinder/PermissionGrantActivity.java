package io.benwiegand.projection.geargrinder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.benwiegand.projection.geargrinder.permission.PermissionGrantUi;

public class PermissionGrantActivity extends AppCompatActivity {
    private static final String TAG = PermissionGrantActivity.class.getSimpleName();

    public static final String INTENT_ACTION_REQUEST_PERMISSION = "io.benwiegand.projection.geargrinder.REQUEST_PERMISSION";

    public static final String INTENT_EXTRA_PERMISSION_KEY = "key";

    private PermissionGrantUi permissionGrantUi;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permission_grant);
        setSupportActionBar(findViewById(R.id.action_bar));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        permissionGrantUi = new PermissionGrantUi(this);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        permissionGrantUi.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionGrantUi.refresh(findViewById(R.id.root));
        onIntent(getIntent());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        if (intent == null) return;
        setIntent(null);

        Log.d(TAG, "handling intent: " + intent);
        switch (intent.getAction()) {
            case INTENT_ACTION_REQUEST_PERMISSION -> {
                String key = intent.getStringExtra(INTENT_EXTRA_PERMISSION_KEY);
                if (key == null) {
                    Log.e(TAG, "missing permission key in intent extra");
                    return;
                }

                Log.d(TAG, "requesting permission due to intent: " + key);
                permissionGrantUi.manualRequest(key);
            }
            case null -> Log.d(TAG, "intent action is null");
            default -> Log.e(TAG, "unhandled intent action: " + intent.getAction());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionGrantUi.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}