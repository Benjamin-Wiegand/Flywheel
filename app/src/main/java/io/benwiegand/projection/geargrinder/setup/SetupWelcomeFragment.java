package io.benwiegand.projection.geargrinder.setup;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.openLink;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.benwiegand.projection.geargrinder.R;

public class SetupWelcomeFragment extends SetupFragment {

    public SetupWelcomeFragment() {
        super(R.layout.layout_setup_welcome);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View sourceCodeButton = view.findViewById(R.id.source_code_button);
        View wikiButton = view.findViewById(R.id.wiki_button);
        View licenseButton = view.findViewById(R.id.license_button);
        View issueTrackerButton = view.findViewById(R.id.issue_tracker_button);

        sourceCodeButton.setOnClickListener(v ->
                openLink(requireActivity(), R.string.source_code_link));

        wikiButton.setOnClickListener(v ->
                openLink(requireActivity(), R.string.wiki_link));

        licenseButton.setOnClickListener(v ->
                openLink(requireActivity(), R.string.license_link));

        issueTrackerButton.setOnClickListener(v ->
                openLink(requireActivity(), R.string.issue_tracker_link));
    }
}
