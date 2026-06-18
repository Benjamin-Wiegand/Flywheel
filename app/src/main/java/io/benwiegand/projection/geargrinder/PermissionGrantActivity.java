package io.benwiegand.projection.geargrinder;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_ENTRIES;
import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_KEY_ROOT;
import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_KEY_SHIZUKU;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.permission.PermissionEntry;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import rikka.shizuku.Shizuku;

public class PermissionGrantActivity extends AppCompatActivity {
    private static final String TAG = PermissionGrantActivity.class.getSimpleName();

    public static final String INTENT_ACTION_REQUEST_PERMISSION = "io.benwiegand.projection.geargrinder.REQUEST_PERMISSION";

    public static final String INTENT_EXTRA_PERMISSION_KEY = "key";

    private static class PermissionEntryHolder {
        private PermissionEntry entry = null;
        private View view = null;
        private boolean granted = false;
    }

    private final Map<String, PermissionEntryHolder> permissionEntryMap = new HashMap<>();

    private SettingsManager settingsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_permission_grant);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Shizuku.addRequestPermissionResultListener(this::onShizukuPermissionResult);
        settingsManager = new SettingsManager(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(this::onShizukuPermissionResult);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
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

                PermissionEntryHolder holder = permissionEntryMap.get(key);
                if (holder == null) {
                    Log.e(TAG, "no permission found for key: " + key);
                    return;
                }

                if (holder.entry.request() == null) {
                    Log.e(TAG, "permission cannot be requested: " + key);
                    return;
                }

                Log.i(TAG, "requesting permission due to intent: " + key);
                holder.entry.request().accept(this);
            }
            case null -> Log.d(TAG, "intent action is null");
            default -> Log.e(TAG, "unhandled intent action: " + intent.getAction());
        }
    }

    private void showEntryGranted(View view, PermissionEntry entry) {
        Button grantButton = view.findViewById(R.id.grant_button);
        Button manageButton = view.findViewById(R.id.manage_button);
        grantButton.setEnabled(false);
        grantButton.setText(entry.getGrantedIndicatorText());

        if (entry.manage() != null && entry.request() == null) {
            manageButton.setVisibility(View.VISIBLE);
            grantButton.setVisibility(View.GONE);
        }
    }

    private void onPermissionGranted(String key) {
        Log.i(TAG, "permission granted: " + key);
        PermissionEntryHolder holder = permissionEntryMap.get(key);
        if (holder == null) {
            Log.wtf(TAG, "no permission entry holder for key: " + key);
            return;
        }

        holder.granted = true;
        showEntryGranted(holder.view, holder.entry);
    }

    private View inflatePermissionEntry(ViewGroup parent, PermissionEntry entry) {
        View view = getLayoutInflater().inflate(R.layout.layout_permission_grant_entry, parent, false);
        TextView titleText = view.findViewById(R.id.title_text);
        ImageButton showRationaleButton = view.findViewById(R.id.show_rationale_button);
        Button manageButton = view.findViewById(R.id.manage_button);
        Button grantButton = view.findViewById(R.id.grant_button);

        titleText.setText(entry.title());
        grantButton.setText(entry.getGrantButtonText());

        showRationaleButton.setOnClickListener(v ->
                entry.createRationaleDialog(this)
                        .setPositiveButton(R.string.close_button, null)
                        .setNeutralButton(null, null)
                        .show());

        grantButton.setOnClickListener(v -> {
            if (entry.shouldShowRationale() != null && entry.shouldShowRationale().apply(this)) {
                entry.createRationaleDialog(this).show();
            } else if (entry.request() != null) {
                entry.request().accept(this);
            } else if (entry.manage() != null) {
                entry.manage().accept(this);
            } else {
                Log.wtf(TAG, "no grant action for permission entry: " + entry, new AssertionError());
            }
        });

        if (entry.manage() != null)
            manageButton.setOnClickListener(v ->
                    entry.manage().accept(this));

        return view;
    }

    private void refreshPermissions() {
        Log.i(TAG, "refreshing permissions");

        LinearLayout requiredPermissionList = findViewById(R.id.required_permission_list);
        LinearLayout optionalPermissionList = findViewById(R.id.optional_permission_list);
        Map<String, PermissionEntryHolder> oldPermissionEntryMap = Map.copyOf(permissionEntryMap);

        permissionEntryMap.clear();
        requiredPermissionList.removeAllViews();
        optionalPermissionList.removeAllViews();


        for (PermissionEntry permissionEntry : PERMISSION_ENTRIES) {
            if (!permissionEntry.usedByConfiguration().apply(settingsManager))
                continue;

            PermissionEntryHolder holder = oldPermissionEntryMap.get(permissionEntry.key());
            if (holder == null) holder = new PermissionEntryHolder();
            holder.entry = permissionEntry;
            holder.view = null;

            if (holder.entry.check() != null)   // don't re-check permissions that can't differentiate requesting and checking (like root)
                holder.granted = permissionEntry.check().apply(this);

            permissionEntryMap.put(permissionEntry.key(), holder);


            LinearLayout permissionList = permissionEntry.requiredByConfiguration().apply(settingsManager) ? requiredPermissionList : optionalPermissionList;

            holder.view = inflatePermissionEntry(permissionList, holder.entry);
            if (holder.granted)
                showEntryGranted(holder.view, holder.entry);

            permissionList.addView(holder.view);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.i(TAG, "request permissions result: \n  - permissions = " + Arrays.toString(permissions) + "\n  - results = " + Arrays.toString(grantResults));

        boolean deniedPermissions = false;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PERMISSION_GRANTED) {
                onPermissionGranted(permissions[i]);
                continue;
            }

            if (permissions[i].equals(PERMISSION_KEY_ROOT)) continue;

            deniedPermissions = true;
        }

        if (deniedPermissions) {
            // if the user accidentally denies the permission, the system may block further permission requests
            try {
                Log.e(TAG, "permission not granted, launching settings");
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Log.e(TAG, "failed to launch app info settings page", t);
            }
        }
    }

    private void onShizukuPermissionResult(int requestCode, int result) {
        if (result == PERMISSION_GRANTED) {
            onPermissionGranted(PERMISSION_KEY_SHIZUKU);
            return;
        }

        Log.e(TAG, "shizuku permission denied (" + result + ")");
    }

}