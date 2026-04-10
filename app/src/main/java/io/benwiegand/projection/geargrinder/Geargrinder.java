package io.benwiegand.projection.geargrinder;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class Geargrinder extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

}
