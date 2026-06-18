package io.benwiegand.projection.geargrinder.permission;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.os.Build;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public record PermissionEntry(
        String key,
        @StringRes int title,
        @StringRes int rationale,
        Function<SettingsManager, Boolean> usedByConfiguration,
        Function<SettingsManager, Boolean> requiredByConfiguration,
        Function<Activity, Boolean> check,
        Function<Activity, Boolean> shouldShowRationale,
        Consumer<Activity> request,
        Consumer<Activity> manage
) {

    public @StringRes int getGrantButtonText() {
        if (request() == null && manage() != null)
            return R.string.enable_service_button;

        if (check() == null)    // checking will also request the permission
            return R.string.check_permission_button;

        return R.string.grant_permission_button;
    }

    public @StringRes int getGrantedIndicatorText() {
        if (request() == null && manage() != null)
            return R.string.service_enabled_indicator;

        return R.string.permission_granted_indicator;
    }

    public AlertDialog.Builder createRationaleDialog(Activity activity) {
        return new AlertDialog.Builder(activity)
                .setTitle(title())
                .setMessage(rationale())
                .setPositiveButton(R.string.grant_permission_button, (d, i) -> request().accept(activity))
                .setNeutralButton(R.string.cancel_button, null);
    }


    static PermissionEntry createForManifestPermission(String permission, Integer minSdk, Integer maxSdk, @StringRes int title, @StringRes int rationale, Function<SettingsManager, Boolean> usedByConfiguration, Function<SettingsManager, Boolean> requiredByConfiguration) {
        return new PermissionEntry(
                permission, title, rationale,
                settingsManager -> (minSdk == null || minSdk <= Build.VERSION.SDK_INT)
                        && (maxSdk == null || maxSdk >= Build.VERSION.SDK_INT)
                        && (usedByConfiguration == null || usedByConfiguration.apply(settingsManager)),
                requiredByConfiguration,
                activity -> ActivityCompat.checkSelfPermission(activity, permission) == PERMISSION_GRANTED,
                activity -> ActivityCompat.shouldShowRequestPermissionRationale(activity, permission),
                activity -> ActivityCompat.requestPermissions(activity, new String[] {permission}, 69),
                null
        );
    }
}
