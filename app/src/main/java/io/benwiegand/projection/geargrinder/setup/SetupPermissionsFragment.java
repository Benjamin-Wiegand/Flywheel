package io.benwiegand.projection.geargrinder.setup;

import android.view.View;

import androidx.appcompat.app.AlertDialog;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.permission.PermissionGrantUi;

public class SetupPermissionsFragment extends SetupFragment {

    public SetupPermissionsFragment() {
        super(R.layout.layout_setup_permissions);
    }

    private PermissionGrantUi requirePermissionGrantUi() {
        return getSetupActivity().orElseThrow().getPermissionGrantUi();
    }

    @Override
    public void onResume() {
        super.onResume();
        requirePermissionGrantUi().refresh(requireView());
    }

    @Override
    protected void onNextButtonClick(View view) {
        if (requirePermissionGrantUi().checkRequired()) {
            super.onNextButtonClick(view);
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.missing_required_permissions_dialog_title)
                .setMessage(R.string.missing_required_permissions_dialog_message)
                .setPositiveButton(R.string.cancel_button, null)
                .setNegativeButton(R.string.proceed_anyway_button, (d, i) -> super.onNextButtonClick(view))
                .show();
    }
}
