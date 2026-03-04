package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import io.benwiegand.projection.geargrinder.util.ShellUtil;

public class RootPrivdLauncher extends PrivdLauncher {
    private static final String TAG = RootPrivdLauncher.class.getSimpleName();

    private Process rootProcess = null;

    public RootPrivdLauncher(Context context) {
        super(
                context,
                context.getCodeCacheDir().toPath().resolve(PRIVD_FILE_NAME).toFile(),
                context.getCodeCacheDir().toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile(),
                context.getFilesDir().toPath().resolve(TOKEN_FILE_NAME).toFile()
        );
    }

    public void destroy() {
        killProcess();
    }

    public void launch() throws IOException {
        init();

        Log.i(TAG, "launching privd as root");
        killProcess();
        executeDaemon();
    }

    private void killProcess() {
        if (rootProcess == null) return;
        Log.d(TAG, "killing privd root process");
        rootProcess.destroyForcibly();
        rootProcess = null;
    }

    private void executeDaemon() throws IOException {
        assert rootProcess == null;

        String command = "sh " + ShellUtil.wrapSingleQuote(launchScriptFile.getAbsolutePath());
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);

        // privd will fall back to reading the token file if this doesn't make it through su
        if (token != null)
            processBuilder.environment().put(ENV_TOKEN, Base64.encodeToString(token, Base64.NO_WRAP));

        rootProcess = processBuilder.start();

        new Thread(() -> {
            try (InputStream is = rootProcess.getInputStream();
                 InputStream es = rootProcess.getErrorStream()) {

                Reader outReader = new InputStreamReader(is, StandardCharsets.UTF_8);
                Reader errReader = new InputStreamReader(es, StandardCharsets.UTF_8);

                char[] buffer = new char[2048];
                int len;

                while (rootProcess.isAlive()) {
                    while ((len = outReader.read(buffer)) > 0)
                        Log.v(TAG, "STDOUT: " + new String(buffer, 0, len));

                    while ((len = errReader.read(buffer)) > 0)
                        Log.e(TAG, "STDERR: " + new String(buffer, 0, len));

                }

                Log.e(TAG, "TERMINATED: " + rootProcess.exitValue());
            } catch (IOException e) {
                Log.w(TAG, "root process loop threw", e);
            }
        }).start();
    }

}
