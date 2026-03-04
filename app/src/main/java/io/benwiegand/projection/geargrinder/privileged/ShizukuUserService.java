package io.benwiegand.projection.geargrinder.privileged;

import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.benwiegand.projection.geargrinder.IShizukuUserService;

public class ShizukuUserService extends IShizukuUserService.Stub {
    private static final String TAG = ShizukuUserService.class.getSimpleName();

    private Process privdProcess = null;

    @Override
    public void execPrivd(String scriptPath, Map<String, String> env) throws RemoteException {
        if (privdProcess != null) killPrivd();

        try {
            Log.v(TAG, "executing privd launcher script at: " + scriptPath);
            ProcessBuilder builder = new ProcessBuilder("sh", scriptPath);
            builder.environment().putAll(env);
            privdProcess = builder.start();

            new Thread(() -> {
                try (InputStream is = privdProcess.getInputStream();
                     InputStream es = privdProcess.getErrorStream()) {

                    Reader outReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                    Reader errReader = new InputStreamReader(es, StandardCharsets.UTF_8);

                    char[] buffer = new char[2048];
                    int len;

                    while (privdProcess.isAlive()) {
                        while ((len = outReader.read(buffer)) > 0)
                            Log.v(TAG, "STDOUT: " + new String(buffer, 0, len));

                        while ((len = errReader.read(buffer)) > 0)
                            Log.e(TAG, "STDERR: " + new String(buffer, 0, len));

                    }

                    Log.e(TAG, "TERMINATED: " + privdProcess.exitValue());
                } catch (IOException e) {
                    Log.w(TAG, "shizuku process loop threw", e);
                }
            }).start();

        } catch (IOException e) {
            Log.e(TAG, "failed to start privd");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void killPrivd() throws RemoteException {
        if (privdProcess == null) return;
        if (!privdProcess.isAlive()) {
            privdProcess = null;
            return;
        }

        Log.i(TAG, "killing existing privd process");
        privdProcess.destroyForcibly();
        privdProcess = null;
    }

    @Override
    public void destroy() throws RemoteException {
        killPrivd();
    }
}
