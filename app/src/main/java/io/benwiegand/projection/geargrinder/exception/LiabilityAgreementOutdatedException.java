package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;
import android.content.Intent;

import io.benwiegand.projection.geargrinder.LiabilityDisclaimerActivity;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.interfaces.ErrorActionIntent;

public class LiabilityAgreementOutdatedException extends UserFriendlyException implements ErrorActionIntent {
    private final Intent actionIntent;

    public LiabilityAgreementOutdatedException(Context c) {
        super(c, R.string.liability_disclaimer_title, R.string.liability_disclaimer_updated_dialog_message);
        actionIntent = new Intent(c, LiabilityDisclaimerActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    public Intent getActionIntent() {
        return actionIntent;
    }

    @Override
    public int getActionTitle() {
        return R.string.view_disclaimer_button;
    }
}
