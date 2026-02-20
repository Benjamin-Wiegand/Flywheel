package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_PORT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_A;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_B;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.util.ShellUtil;

public class RootPrivdLauncher {
    private static final String TAG = RootPrivdLauncher.class.getSimpleName();
    private static final long DAEMON_LAUNCH_COOLDOWN = 5000;

    private static final String PRIVD_FILE_NAME = "privd.jar";
    private static final String LAUNCH_SCRIPT_FILE_NAME = "launch-privd.sh";

    private final Context context;
    private final IPCConnectionListener connectionListener;
    private final File daemonFile;
    private final File launchScriptFile;

    private long lastLaunched = 0;

    private IPCServer server = null;
    private Process rootProcess = null;

    public RootPrivdLauncher(Context context, IPCConnectionListener connectionListener) {
        this.context = context;
        this.connectionListener = connectionListener;
        daemonFile = context.getCodeCacheDir().toPath().resolve(PRIVD_FILE_NAME).toFile();
        launchScriptFile = context.getCodeCacheDir().toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile();

        // TODO: for launching non-root (less secure)
//        daemonFile = context.getExternalFilesDir(null).toPath().resolve(PRIVD_FILE_NAME).toFile();
//        launchScriptFile = context.getExternalFilesDir(null).toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile();
    }

    public void destroy() {
        killRootProcess();
        killServer();
    }

    public void launchRoot() throws IOException {
        if (lastLaunched + DAEMON_LAUNCH_COOLDOWN > SystemClock.elapsedRealtime()) {
            Log.d(TAG, "not launching daemon, already launched it " + (SystemClock.elapsedRealtime() - lastLaunched) + " ms ago");
            return;
        }
        lastLaunched = SystemClock.elapsedRealtime();

        killRootProcess();
        copyDaemon();
        startServer();
        generateScript();
        executeDaemon();
    }

    private void killServer() {
        if (server == null) return;
        Log.i(TAG, "closing IPC server");
        server.close();
        server = null;
    }

    private void killRootProcess() {
        if (rootProcess == null) return;
        Log.i(TAG, "killing privd root process");
        rootProcess.destroyForcibly();
        rootProcess = null;
    }

    private void startServer() throws IOException {
        if (server != null) {
            server.rotate();
            return;
        }

        Log.i(TAG, "starting IPC server");
        server = new IPCServer(connectionListener);
        server.start();
    }

    private void executeDaemon() throws IOException {
        assert rootProcess == null;
        assert server != null;
        Log.i(TAG, "launching privd as root");

        String command = "sh " + ShellUtil.wrapSingleQuote(launchScriptFile.getAbsolutePath());
        rootProcess = new ProcessBuilder("su", "-c", command).start();
    }

    private void copyDaemon() throws IOException {
        if (daemonFile.isFile()) {
            Log.d(TAG, "deleting " + daemonFile);
            if (!daemonFile.delete()) throw new IOException("failed to delete existing copy of the daemon");
        }

        Log.i(TAG, "copying " + PRIVD_FILE_NAME + " to " + daemonFile);
        try (InputStream is = context.getAssets().open(PRIVD_FILE_NAME);
             FileOutputStream os = new FileOutputStream(daemonFile)) {
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) >= 0)
                os.write(buffer, 0, len);
        }
    }

    private void generateScript() throws IOException {
        String script = """
        #!/bin/sh
        
        set -e
        
        # generated vars
        """;

        script += "export " + ENV_PORT + "=" + server.getPort() + "\n";
        script += "export " + ENV_TOKEN_A + "=" + Base64.encodeToString(server.getTokenB(), Base64.NO_WRAP) + "\n";
        script += "export " + ENV_TOKEN_B + "=" + Base64.encodeToString(server.getTokenA(), Base64.NO_WRAP) + "\n";
        script += "INIT_JAR_PATH=" + ShellUtil.wrapSingleQuote(daemonFile.getAbsolutePath()) + "\n";

        script += """
        # end generated vars
        
        PRIVD_NAME=Geargrinder-privd
        EXEC_JAR_PATH="/data/local/tmp/geargrinder-privd.jar"
        
        if ! [[ -f "$INIT_JAR_PATH" ]]; then
            echo "file not found: $INIT_JAR_PATH"
            exit 1
        fi
        
        cp -v "$INIT_JAR_PATH" "$EXEC_JAR_PATH"
        chown -v "`id -u`:`id -g`" "$EXEC_JAR_PATH"
        chmod -v 0400 "$EXEC_JAR_PATH"
        export CLASSPATH="$EXEC_JAR_PATH"
        
        set +e
        
        echo "launching $PRIVD_NAME as $USER (`id -u`)"
        app_process /system/bin --nice-name=$PRIVD_NAME io.benwiegand.projection.geargrinder.privd.Main
        exit_code=$?
        echo "app_process exited with code $exit_code"
        exit $exit_code
        """;

        Log.i(TAG, "dumping launch script to " + launchScriptFile);
        try (FileOutputStream os = new FileOutputStream(launchScriptFile)) {
            os.write(script.getBytes(StandardCharsets.UTF_8));
        }
    }


}
