package io.benwiegand.projection.geargrinder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class LiabilityDisclaimerActivity extends AppCompatActivity {

    public static final long DISCLAIMER_AGREE_COOLDOWN_DELAY = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SettingsManager settingsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_liability_disclaimer);
        setSupportActionBar(findViewById(R.id.action_bar));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        settingsManager = new SettingsManager(this);

        findViewById(R.id.agree_button).setOnClickListener(v -> {
            settingsManager.saveLiabilityAgreementVersion(SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT);
            finish();
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        Button agreeButton = findViewById(R.id.agree_button);

        if (settingsManager.getLiabilityAgreementVersion() != SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT) {
            agreeButton.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> agreeButton.setEnabled(true), DISCLAIMER_AGREE_COOLDOWN_DELAY);
        } else {
            agreeButton.setVisibility(View.GONE);
        }

    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

}
