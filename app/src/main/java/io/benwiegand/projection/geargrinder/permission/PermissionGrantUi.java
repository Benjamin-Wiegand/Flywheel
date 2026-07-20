package io.benwiegand.projection.geargrinder.permission;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_ENTRIES;
import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_KEY_ROOT;
import static io.benwiegand.projection.geargrinder.permission.PermissionRequirements.PERMISSION_KEY_SHIZUKU;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import rikka.shizuku.Shizuku;

public class PermissionGrantUi implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String TAG = PermissionGrantUi.class.getSimpleName();

    private static class PermissionEntryHolder {
        private PermissionEntry entry = null;
        private View view = null;
        private boolean granted = false;
    }

    private final Activity activity;
    private final Map<String, PermissionEntryHolder> permissionEntryMap = new HashMap<>();
    private final SettingsManager settingsManager;

    public PermissionGrantUi(Activity activity) {
        this.activity = activity;
        settingsManager = new SettingsManager(activity);
        Shizuku.addRequestPermissionResultListener(this::onShizukuPermissionResult);
    }

    public void destroy() {
        Shizuku.removeRequestPermissionResultListener(this::onShizukuPermissionResult);
    }

    public void refresh(View rootView) {
        Log.i(TAG, "refreshing permissions");

        LinearLayout requiredPermissionList = rootView.findViewById(R.id.required_permission_list);
        LinearLayout optionalPermissionList = rootView.findViewById(R.id.optional_permission_list);
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
                holder.granted = permissionEntry.check().apply(activity);

            permissionEntryMap.put(permissionEntry.key(), holder);


            LinearLayout permissionList = permissionEntry.requiredByConfiguration().apply(settingsManager) ? requiredPermissionList : optionalPermissionList;

            holder.view = inflatePermissionEntry(permissionList, holder.entry);
            if (holder.granted)
                showEntryGranted(holder.view, holder.entry);

            permissionList.addView(holder.view);
        }
    }

    public void manualRequest(String key) {
        PermissionEntryHolder holder = permissionEntryMap.get(key);
        if (holder == null) {
            Log.e(TAG, "no permission found for key: " + key);
            return;
        }

        if (holder.entry.request() == null) {
            Log.e(TAG, "permission cannot be requested: " + key);
            return;
        }

        Log.i(TAG, "requesting permission: " + key);
        holder.entry.request().accept(activity);

    }

    public boolean checkRequired() {
        for (PermissionEntryHolder holder: permissionEntryMap.values()) {
            if (!holder.entry.requiredByConfiguration().apply(settingsManager)) continue;
            if (holder.granted) continue;
            Log.i(TAG, "missing: " + holder.entry);
            return false;
        }
        return true;
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
        View view = activity.getLayoutInflater().inflate(R.layout.layout_permission_grant_entry, parent, false);
        TextView titleText = view.findViewById(R.id.title_text);
        ImageButton showRationaleButton = view.findViewById(R.id.show_rationale_button);
        Button manageButton = view.findViewById(R.id.manage_button);
        Button grantButton = view.findViewById(R.id.grant_button);

        titleText.setText(entry.title());
        grantButton.setText(entry.getGrantButtonText());

        showRationaleButton.setOnClickListener(v ->
                entry.createRationaleDialog(activity)
                        .setPositiveButton(R.string.close_button, null)
                        .setNeutralButton(null, null)
                        .show());

        grantButton.setOnClickListener(v -> {
            if (entry.shouldShowRationale() != null && entry.shouldShowRationale().apply(activity)) {
                entry.createRationaleDialog(activity).show();
            } else if (entry.request() != null) {
                entry.request().accept(activity);
            } else if (entry.manage() != null) {
                entry.manage().accept(activity);
            } else {
                Log.wtf(TAG, "no grant action for permission entry: " + entry, new AssertionError());
            }
        });

        if (entry.manage() != null)
            manageButton.setOnClickListener(v ->
                    entry.manage().accept(activity));

        return view;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
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
