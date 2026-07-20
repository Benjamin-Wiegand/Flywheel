package io.benwiegand.projection.geargrinder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentTransaction;

import io.benwiegand.projection.geargrinder.permission.PermissionGrantUi;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.setup.SetupFinishFragment;
import io.benwiegand.projection.geargrinder.setup.SetupFragment;
import io.benwiegand.projection.geargrinder.setup.SetupKeysFragment;
import io.benwiegand.projection.geargrinder.setup.SetupLiabilityAgreementFragment;
import io.benwiegand.projection.geargrinder.setup.SetupPermissionsFragment;
import io.benwiegand.projection.geargrinder.setup.SetupWelcomeFragment;
import io.benwiegand.projection.geargrinder.setup.SetupPrivilegeLevelFragment;

public class SetupActivity extends AppCompatActivity {
    private static final String TAG = SetupActivity.class.getSimpleName();

    private SettingsManager settingsManager;
    private int setupIndex = 0;

    private PermissionGrantUi permissionGrantUi;

    private final SetupFragment[] setupFragments = new SetupFragment[] {
            new SetupWelcomeFragment(),
            new SetupLiabilityAgreementFragment(),
            new SetupPrivilegeLevelFragment(),
            new SetupPermissionsFragment(),
            new SetupKeysFragment(),
            new SetupFinishFragment(),
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        settingsManager = new SettingsManager(this);
        permissionGrantUi = new PermissionGrantUi(this);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_frame, setupFragments[setupIndex])
                .commit();

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                prevPage();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        permissionGrantUi.destroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionGrantUi.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("setupIndex", setupIndex);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int index = savedInstanceState.getInt("setupIndex", setupIndex);
        goToSetupIndex(index);
    }

    private void goToSetupIndex(int index) {
        if (index == setupIndex) return;
        int transition = index > setupIndex ? FragmentTransaction.TRANSIT_FRAGMENT_OPEN : FragmentTransaction.TRANSIT_FRAGMENT_CLOSE;

        setupIndex = index;
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.setup_frame, setupFragments[setupIndex])
                .setTransition(transition)
                .commit();
    }

    public void nextPage() {
        if (setupIndex >= setupFragments.length - 1) {
            Log.i(TAG, "setup finished");
            settingsManager.saveSetupVersion(SettingsManager.SETUP_VERSION_CURRENT);
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        Log.d(TAG, "next page");
        goToSetupIndex(setupIndex + 1);
    }

    public void prevPage() {
        if (setupIndex <= 0) return;

        Log.d(TAG, "previous page");
        goToSetupIndex(setupIndex - 1);
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public PermissionGrantUi getPermissionGrantUi() {
        return permissionGrantUi;
    }

}
