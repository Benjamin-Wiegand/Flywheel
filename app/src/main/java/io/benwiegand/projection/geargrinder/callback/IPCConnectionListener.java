package io.benwiegand.projection.geargrinder.callback;

import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.libprivd.IPrivd;

public interface IPCConnectionListener {
    void onPrivdConnected(IPrivd privd);
    void onPrivdDisconnected();
    void onPrivdLaunchFailure(UserFriendlyException e);
}
