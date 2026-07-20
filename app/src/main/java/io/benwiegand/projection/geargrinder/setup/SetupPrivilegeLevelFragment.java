package io.benwiegand.projection.geargrinder.setup;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.permission.PermissionRequirements;
import io.benwiegand.projection.geargrinder.settings.PrivilegeMode;

public class SetupPrivilegeLevelFragment extends SetupFragment {

    public SetupPrivilegeLevelFragment() {
        super(R.layout.layout_setup_privilege_level);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RadioGroup privilegeLevelGroup = view.findViewById(R.id.privilege_level_selection_group);
        Button nextButton = view.findViewById(R.id.next_button);
        Button rootButton = view.findViewById(R.id.root_button);
        Button shizukuButton = view.findViewById(R.id.shizuku_button);

        privilegeLevelGroup.setOnCheckedChangeListener((v, checkedId) -> {
            if (checkedId == R.id.root_button) {
                nextButton.setEnabled(true);
                requireSettingsManager().savePrivilegeMode(PrivilegeMode.ROOT);
            } else if (checkedId == R.id.shizuku_button) {
                nextButton.setEnabled(true);
                requireSettingsManager().savePrivilegeMode(PrivilegeMode.SHIZUKU);
            } else {
                nextButton.setEnabled(false);
            }
        });

        rootButton.setOnClickListener(v ->
                PermissionRequirements.ROOT_PERMISSION_ENTRY.request().accept(requireActivity()));

        shizukuButton.setOnClickListener(v ->
                PermissionRequirements.SHIZUKU_PERMISSION_ENTRY.request().accept(requireActivity()));
    }

}
