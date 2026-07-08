package io.benwiegand.projection.geargrinder;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.File;

import io.benwiegand.projection.geargrinder.logs.LogcatReader;

public class LogService extends Service implements LogcatReader.UiLogListener {

    private final LogcatReader logcatReader = new LogcatReader();
    private final ServiceBinder binder = new ServiceBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        logcatReader.registerUiListener(this);
        logcatReader.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logcatReader.unregisterUiListener(this);
        logcatReader.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onRecordingStart(File file) {
        startService(new Intent(this, LogService.class));
    }

    @Override
    public void onRecordingStop() {
        stopService(new Intent(this, LogService.class));
    }

    public class ServiceBinder extends Binder {

        public LogcatReader getLogcatReader() {
            return logcatReader;
        }

    }

}
