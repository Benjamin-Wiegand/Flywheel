package io.benwiegand.projection.geargrinder.permission;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.tryLaunchIntents;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import io.benwiegand.projection.geargrinder.NotificationService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.SettingsActivity;
import io.benwiegand.projection.geargrinder.settings.PrivilegeMode;
import rikka.shizuku.Shizuku;

public class PermissionRequirements {
    private static final String TAG = PermissionRequirements.class.getSimpleName();

    private static final String SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api";

    public static final String PERMISSION_KEY_ROOT = "ROOT";
    public static final String PERMISSION_KEY_SHIZUKU = "SHIZUKU";


    @SuppressLint("InlinedApi")
    public static final PermissionEntry POST_NOTIFICATIONS_PERMISSION_ENTRY = PermissionEntry.createForManifestPermission(
            Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU, null,
            R.string.post_notifications_permission_title, R.string.post_notifications_permission_rationale,
            settingsManager -> true, settingsManager -> true);

    public static final PermissionEntry RECORD_AUDIO_PERMISSION_ENTRY = PermissionEntry.createForManifestPermission(
            Manifest.permission.RECORD_AUDIO, Build.VERSION_CODES.Q, null,  // doesn't seem to be needed on newer android versions, but the docs still say otherwise
            R.string.record_audio_permission_title, R.string.record_audio_permission_rationale,
            settingsManager -> true, settingsManager -> true);

    public static final PermissionEntry READ_PHONE_STATE_PERMISSION_ENTRY = PermissionEntry.createForManifestPermission(
            Manifest.permission.READ_PHONE_STATE, Build.VERSION_CODES.Q, Build.VERSION_CODES.R,
            R.string.read_phone_state_permission_title, R.string.read_phone_state_permission_rationale,
            settingsManager -> true, settingsManager -> false);

    @SuppressLint("InlinedApi")
    public static final PermissionEntry BLUETOOTH_CONNECT_PERMISSION_ENTRY = PermissionEntry.createForManifestPermission(
            Manifest.permission.BLUETOOTH_CONNECT, Build.VERSION_CODES.S, null,
            R.string.bluetooth_connect_permission_title, R.string.bluetooth_connect_permission_rationale,
            settingsManager -> true, settingsManager -> false);

    public static final PermissionEntry ACCESS_FINE_LOCATION_PERMISSION_ENTRY = PermissionEntry.createForManifestPermission(
            Manifest.permission.ACCESS_FINE_LOCATION, Build.VERSION_CODES.Q, Build.VERSION_CODES.R,
            R.string.access_fine_location_permission_title, R.string.access_fine_location_permission_rationale,
            settingsManager -> true, settingsManager -> false);

    public static final PermissionEntry NOTIFICATION_LISTENER_PERMISSION_ENTRY = new PermissionEntry(
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            R.string.notification_service_permission_title,
            R.string.notification_service_permission_rationale,
            settingsManager -> true,
            settingsManager -> false,
            activity -> NotificationManagerCompat
                    .getEnabledListenerPackages(activity)
                    .contains(activity.getPackageName()),
            null,
            null,
            activity -> tryLaunchIntents(activity,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                                                     .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName.createRelative(activity, NotificationService.class.getName()).flattenToString()) : null,
                    new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS)
            )
    );

    public static final PermissionEntry ROOT_PERMISSION_ENTRY = new PermissionEntry(
            PERMISSION_KEY_ROOT, R.string.root_permission_title, R.string.root_permission_rationale,
            settingsManager -> settingsManager.getPrivilegeMode() == PrivilegeMode.ROOT,
            settingsManager -> true,
            null,
            null,
            activity -> new Thread(() -> {
                boolean success = false;
                try {
                    Log.i(TAG, "performing root check");
                    success = new ProcessBuilder("su", "-c", "echo testing123")
                            .start()
                            .waitFor() == 0;
                } catch (Throwable t) {
                    Log.e(TAG, "failed to check/request root permission", t);
                }

                int result = success ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
                activity.runOnUiThread(() -> activity.onRequestPermissionsResult(69, new String[] {PERMISSION_KEY_ROOT}, new int[] {result}));
            }).start(),
            null
    );

    public static final PermissionEntry SHIZUKU_PERMISSION_ENTRY = new PermissionEntry(
            PERMISSION_KEY_SHIZUKU, R.string.shizuku_permission_title, R.string.shizuku_permission_rationale,
            settingsManager -> settingsManager.getPrivilegeMode() == PrivilegeMode.SHIZUKU,
            settingsManager -> true,
            activity -> !Shizuku.isPreV11() &&
                    Shizuku.getBinder() != null &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED,
            activity -> !Shizuku.isPreV11() &&
                    Shizuku.getBinder() != null &&
                    Shizuku.shouldShowRequestPermissionRationale(),
            activity -> {
                Intent shizukuLaunchIntent = activity.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME);
                AlertDialog.Builder errorDialog = new AlertDialog.Builder(activity)
                        .setTitle(R.string.shizuku_permission_title)
                        .setNeutralButton(R.string.cancel_button, null)
                        .setNegativeButton(R.string.settings_button, (d, i) ->
                                activity.startActivity(new Intent(activity, SettingsActivity.class)));

                if (shizukuLaunchIntent != null)
                    errorDialog.setPositiveButton(R.string.launch_shizuku_button, (d, i) ->
                            activity.startActivity(shizukuLaunchIntent));

                if (Shizuku.isPreV11()) {
                    errorDialog
                            .setMessage(R.string.shizuku_too_old)
                            .show();
                    return;
                }

                if (Shizuku.getBinder() == null) {
                    errorDialog
                            .setMessage(shizukuLaunchIntent == null ? R.string.shizuku_not_installed : R.string.shizuku_not_running)
                            .show();
                    return;
                }

                Shizuku.requestPermission(69);
            },
            null
    );

    public static final List<PermissionEntry> PERMISSION_ENTRIES = List.of(
            ROOT_PERMISSION_ENTRY,
            SHIZUKU_PERMISSION_ENTRY,
            POST_NOTIFICATIONS_PERMISSION_ENTRY,
            RECORD_AUDIO_PERMISSION_ENTRY,
            NOTIFICATION_LISTENER_PERMISSION_ENTRY,
            BLUETOOTH_CONNECT_PERMISSION_ENTRY,
            ACCESS_FINE_LOCATION_PERMISSION_ENTRY,
            READ_PHONE_STATE_PERMISSION_ENTRY
    );
}
