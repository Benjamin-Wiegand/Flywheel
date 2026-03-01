package io.benwiegand.projection.geargrinder.callback;

import io.benwiegand.projection.geargrinder.pm.AppRecord;

public interface AppLauncherListener {
    void onAppSelected(AppRecord app);
}
