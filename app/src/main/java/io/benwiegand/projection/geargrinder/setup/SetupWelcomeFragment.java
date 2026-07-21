package io.benwiegand.projection.geargrinder.setup;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.ISSUE_TRACKER_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.LICENSE_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.SOURCE_CODE_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.WIKI_LINK;
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
                openLink(requireActivity(), SOURCE_CODE_LINK));

        wikiButton.setOnClickListener(v ->
                openLink(requireActivity(), WIKI_LINK));

        licenseButton.setOnClickListener(v ->
                openLink(requireActivity(), LICENSE_LINK));

        issueTrackerButton.setOnClickListener(v ->
                openLink(requireActivity(), ISSUE_TRACKER_LINK));
    }
}
