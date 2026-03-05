package io.benwiegand.projection.geargrinder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Map;
import java.util.function.Supplier;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "requesting notification permission");
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.post_notifications_permission_request)
                        .setMessage(R.string.post_notifications_permission_rationale)
                        .setPositiveButton(R.string.grant_permission_button, (d, i) ->
                                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 69))
                        .setNegativeButton(R.string.not_now_button, null)
                        .setCancelable(false)
                        .show();
            } else {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 69);
            }
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
