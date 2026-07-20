package io.benwiegand.projection.geargrinder.setup;

import static io.benwiegand.projection.geargrinder.util.ActivityUtil.CERTIFICATES_AND_KEYS_INFO_LINK;
import static io.benwiegand.projection.geargrinder.util.ActivityUtil.openLink;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.benwiegand.projection.geargrinder.R;

public class SetupKeysFragment extends SetupFragment {

    public SetupKeysFragment() {
        super(R.layout.layout_setup_keys);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button learnMoreButton = view.findViewById(R.id.learn_more_button);

        learnMoreButton.setOnClickListener(v ->
                openLink(requireActivity(), CERTIFICATES_AND_KEYS_INFO_LINK));

        // TODO: import bundle
    }
}
