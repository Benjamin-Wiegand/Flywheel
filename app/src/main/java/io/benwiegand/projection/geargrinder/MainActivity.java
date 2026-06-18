package io.benwiegand.projection.geargrinder;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.permission.PermissionEntry;
import io.benwiegand.projection.geargrinder.permission.PermissionRequirements;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        SettingsManager settingsManager = new SettingsManager(this);
        for (PermissionEntry entry : PermissionRequirements.PERMISSION_ENTRIES) {
            if (!entry.usedByConfiguration().apply(settingsManager)) continue;
            if (!entry.requiredByConfiguration().apply(settingsManager)) continue;
            if (entry.check() == null || entry.check().apply(this)) continue;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.missing_required_permissions_dialog_title)
                    .setMessage(R.string.missing_required_permissions_dialog_message)
                    .setPositiveButton(R.string.manage_permissions_button, (d, i) ->
                            startActivity(new Intent(this, PermissionGrantActivity.class)))
                    .setNeutralButton(R.string.not_now_button, null)
                    .show();

            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Map<Integer, Supplier<Boolean>> actionMap = Map.of(
                R.id.manage_permissions_button, () -> {
                    startActivity(new Intent(this, PermissionGrantActivity.class));
                    return true;
                },
                R.id.settings_button, () -> {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return false;
                },
                R.id.debug_button, () -> {
                    startActivity(new Intent(this, DebugActivity.class));
                    return true;
                }
        );
        Supplier<Boolean> action = actionMap.getOrDefault(item.getItemId(), () -> super.onOptionsItemSelected(item));
        assert action != null;
        return action.get();
    }
}
