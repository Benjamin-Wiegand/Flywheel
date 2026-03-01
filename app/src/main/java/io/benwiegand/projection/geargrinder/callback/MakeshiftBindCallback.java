package io.benwiegand.projection.geargrinder.callback;

import android.content.Intent;
import android.os.IBinder;

public interface MakeshiftBindCallback {
    IBinder onMakeshiftBind(Intent intent);
}
