package io.benwiegand.projection.geargrinder.setup;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class SetupLiabilityAgreementFragment extends SetupFragment {

    private static final long DISCLAIMER_AGREE_COOLDOWN_DELAY = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    public SetupLiabilityAgreementFragment() {
        super(R.layout.layout_setup_liability_agreement);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SettingsManager settingsManager = requireSettingsManager();

        CheckBox agreeCheckBox = view.findViewById(R.id.agree_checkbox);
        Button nextButton = view.findViewById(R.id.next_button);

        agreeCheckBox.setOnCheckedChangeListener((v, checked) -> {
            settingsManager.saveLiabilityAgreementVersion(
                    checked ? SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT : SettingsManager.LIABILITY_AGREEMENT_VERSION_NONE);
            nextButton.setEnabled(checked);
        });

        agreeCheckBox.setChecked(settingsManager.getLiabilityAgreementVersion() == SettingsManager.LIABILITY_AGREEMENT_VERSION_CURRENT);
    }

    @Override
    public void onResume() {
        super.onResume();
        CheckBox agreeCheckBox = requireView().findViewById(R.id.agree_checkbox);

        if (agreeCheckBox.isChecked()) {
            agreeCheckBox.setEnabled(true);
        } else {
            agreeCheckBox.setEnabled(false);
            handler.postDelayed(() -> agreeCheckBox.setEnabled(true), DISCLAIMER_AGREE_COOLDOWN_DELAY);
        }
    }
}
