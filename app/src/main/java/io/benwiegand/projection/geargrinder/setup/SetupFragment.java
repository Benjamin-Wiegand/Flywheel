package io.benwiegand.projection.geargrinder.setup;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Optional;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.SetupActivity;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class SetupFragment extends Fragment {

    public SetupFragment(@LayoutRes int layout) {
        super(layout);
    }

    protected Optional<SetupActivity> getSetupActivity() {
        if (getActivity() instanceof SetupActivity setupActivity)
            return Optional.of(setupActivity);
        return Optional.empty();
    }

    protected Optional<SettingsManager> getSettingsManager() {
        return getSetupActivity()
                .map(SetupActivity::getSettingsManager);
    }

    protected SettingsManager requireSettingsManager() {
        return getSettingsManager().orElseThrow();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button backButton = view.findViewById(R.id.back_button);
        Button nextButton = view.findViewById(R.id.next_button);

        if (backButton != null) backButton.setOnClickListener(this::onBackButtonClick);

        if (nextButton != null) nextButton.setOnClickListener(this::onNextButtonClick);

    }

    protected void onBackButtonClick(View view) {
        getSetupActivity().ifPresent(SetupActivity::prevPage);
    }

    protected void onNextButtonClick(View view) {
        getSetupActivity().ifPresent(SetupActivity::nextPage);
    }

}
