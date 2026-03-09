package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_PATH;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.TOKEN_LENGTH;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.settings.PrivilegeMode;
import io.benwiegand.projection.geargrinder.util.ShellUtil;

public abstract class PrivdLauncher {
    private static final String TAG = PrivdLauncher.class.getSimpleName();

    protected static final String PRIVD_FILE_NAME = "privd.jar";
    protected static final String LAUNCH_SCRIPT_FILE_NAME = "launch-privd.sh";
    public static final String TOKEN_FILE_NAME = "privd-token.bin";

    protected final Context context;
    protected final File daemonFile;
    protected final File launchScriptFile;
    protected final File tokenFile;

    private boolean init = false;
    protected byte[] token = null;

    protected Consumer<Throwable> errorListener = null;

    public PrivdLauncher(Context context, File daemonFile, File launchScriptFile, File tokenFile) {
        this.context = context;
        this.daemonFile = daemonFile;
        this.launchScriptFile = launchScriptFile;
        this.tokenFile = tokenFile;
    }

    public abstract void destroy();

    public abstract void launch() throws Throwable;

    public void setErrorListener(Consumer<Throwable> errorListener) {
        this.errorListener = errorListener;
    }

    protected void onError(Throwable throwable) {
        if (errorListener == null) return;
        errorListener.accept(throwable);
    }

    protected void init() throws IOException {
        if (init) return;
        Log.i(TAG, "init privd launcher");
        copyDaemon();
        generateToken();
        generateScript();
        init = true;
    }

    protected void copyDaemon() throws IOException {
        if (daemonFile.isFile()) {
            Log.d(TAG, "deleting " + daemonFile);
            if (!daemonFile.delete()) throw new IOException("failed to delete existing copy of the daemon");
        }

        Log.v(TAG, "copying " + PRIVD_FILE_NAME + " to " + daemonFile);
        try (InputStream is = context.getAssets().open(PRIVD_FILE_NAME);
             FileOutputStream os = new FileOutputStream(daemonFile)) {
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) >= 0)
                os.write(buffer, 0, len);
        }
    }

    protected void generateToken() {
        try {
            byte[] newToken = new byte[TOKEN_LENGTH];

            Log.d(TAG, "generating random " + newToken.length + " byte token");
            Random random = SecureRandom.getInstanceStrong();
            random.nextBytes(newToken);

            Log.v(TAG, "saving token to " + tokenFile);
            try (FileOutputStream os = new FileOutputStream(tokenFile)) {
                os.write(newToken);
            }

            token = newToken;

        } catch (Throwable t) {
            Log.e(TAG, "failed to generate token", t);
            throw new RuntimeException(t);
        }
    }

    private void generateScript() throws IOException {
        String script = """
        #!/bin/sh
        
        set -e
        
        # generated vars
        """;

        script += "INIT_JAR_PATH=" + ShellUtil.wrapSingleQuote(daemonFile.getAbsolutePath()) + "\n";
        script += "export " + ENV_TOKEN_PATH + "=" + ShellUtil.wrapSingleQuote(tokenFile.getAbsolutePath()) + "\n";

        script += """
        # end generated vars
        
        PRIVD_NAME=Geargrinder-privd
        EXEC_JAR_PATH="/data/local/tmp/geargrinder-privd.jar"
        
        if ! [[ -f "$INIT_JAR_PATH" ]]; then
            echo "file not found: $INIT_JAR_PATH"
            exit 1
        fi
        
        echo "preparing"
        rm -f "$EXEC_JAR_PATH"
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

        Log.v(TAG, "saving launch script to " + launchScriptFile);
        try (FileOutputStream os = new FileOutputStream(launchScriptFile)) {
            os.write(script.getBytes(StandardCharsets.UTF_8));
        }
    }

    public byte[] readToken() throws IOException {
        if (!tokenFile.isFile()) return null;

        try (FileInputStream is = new FileInputStream(tokenFile)) {
            byte[] token = new byte[TOKEN_LENGTH];
            int offset = 0;
            int len;
            while (offset < TOKEN_LENGTH) {
                len = is.read(token, offset, TOKEN_LENGTH - offset);
                if (len < 0) {
                    Log.e(TAG, "stored token too short (" + len + " / " + TOKEN_LENGTH + " bytes): " + tokenFile);
                    return null;
                }

                offset += len;
            }

            return token;
        }
    }

    public static PrivdLauncher createForPrivilegeMode(PrivilegeMode mode, Context context) {
        return switch (mode) {
            case NO_ROOT -> null;
            case SHIZUKU -> new ShizukuPrivdLauncher(context);
            case ROOT -> new RootPrivdLauncher(context);
        };
    }

}
