package io.benwiegand.projection.geargrinder.exception.interfaces;

import android.content.Intent;

import androidx.annotation.StringRes;

public interface ErrorActionIntent {

    @StringRes int getActionTitle();
    Intent getActionIntent();

}
