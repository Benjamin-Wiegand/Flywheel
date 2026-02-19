package io.benwiegand.projection.geargrinder.privileged;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import io.benwiegand.projection.libprivd.data.ActivityLaunchParams;
import io.benwiegand.projection.libprivd.data.InjectMotionEventParams;
import io.benwiegand.projection.libprivd.data.IntResult;
import io.benwiegand.projection.libprivd.ipc.IPCConnection;
import io.benwiegand.projection.libprivd.ipc.IPCConstants;
import io.benwiegand.projection.libprivd.sec.Sec;

public class PrivdIPCConnection extends IPCConnection {
    private static final String TAG = PrivdIPCConnection.class.getSimpleName();

    private final IPCServer.ConnectionListener connectionCallback;

    public PrivdIPCConnection(Socket socket, byte[] tokenA, byte[] tokenB, IPCServer.ConnectionListener connectionCallback) throws IOException {
        super(socket, tokenA, tokenB);
        this.connectionCallback = connectionCallback;
    }

    public Sec<Boolean> injectMotionEvent(InjectMotionEventParams params) {
        return send(IPCConstants.COMMAND_INJECT_MOTION_EVENT, params)
                .map(r -> switch (r.status) {
                    case IPCConstants.REPLY_SUCCESS -> r.data[0] != 0;
                    case IPCConstants.REPLY_FAILURE -> throw new RuntimeException("got REPLY_FAILURE from daemon");
                    default -> throw new AssertionError("unexpected reply status from daemon: " + r.status);
                });
    }

    public Sec<Integer> launchActivity(ActivityLaunchParams params) {
        return send(IPCConstants.COMMAND_LAUNCH_ACTIVITY, params)
                .map(r -> switch (r.status) {
                    case IPCConstants.REPLY_SUCCESS -> r.unmarshallParcelableData(IntResult.CREATOR).getResult();
                    case IPCConstants.REPLY_FAILURE -> throw new RuntimeException("got REPLY_FAILURE from daemon");
                    default -> throw new AssertionError("unexpected reply status from daemon: " + r.status);
                });
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onInitComplete(boolean success) {
        connectionCallback.onInitComplete(this, success);
    }

    @Override
    protected Reply onCommand(int command, byte[] data, int offset, int length) {
        return switch (command) {
            case IPCConstants.COMMAND_PING -> {
                Log.d(TAG, "ping");
                yield new Reply(IPCConstants.REPLY_SUCCESS);
            }
            default -> {
                Log.wtf(TAG, "unhandled command: " + command);
                yield new Reply(IPCConstants.REPLY_FAILURE);
            }
        };
    }

    @Override
    protected void onClose() {
        Log.e(TAG, "connection closed");
        connectionCallback.onDisconnected(this);
    }
}
