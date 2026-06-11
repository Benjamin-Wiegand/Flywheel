package io.benwiegand.projection.geargrinder.connector;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.transfer.TcpTransferInterface;

public class AATcpConnector extends AAConnector {
    private static final String TAG = AATcpConnector.class.getSimpleName();
    private static final int PORT_NUMBER = 5277;

    private final Thread thread = new Thread(this::serverThread, "Geargrinder TCP development server");
    private boolean dead = false;

    public AATcpConnector(Context context, StateListener listener, ControlListener controlListener, ConnectionService.ServiceBinder connectionServiceBinder, SettingsManager settingsManager) {
        super(context, listener, controlListener, connectionServiceBinder, settingsManager);
    }

    @Override
    public void stop() {
        dead = true;
        thread.interrupt();
    }

    @Override
    public void start() {
        thread.start();
    }

    private void serverThread() {
        try {
            ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();
            try (ServerSocket serverSocket = serverSocketFactory.createServerSocket(PORT_NUMBER)) {

                while (!dead) {
                    Log.i(TAG, "listening on TCP port " + PORT_NUMBER);
                    listener.onConnectingStatus(R.string.looking_for_car);

                    try (Socket socket = serverSocket.accept()) {
                        Log.i(TAG, "connection from " + socket.getRemoteSocketAddress());
                        listener.onConnected();
                        TcpTransferInterface transferInterface = new TcpTransferInterface(socket);
                        connectionLoop(transferInterface);
                        listener.onDisconnected();
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException while listening for connections", e);
                throw new UserFriendlyException(context, R.string.car_connection_unexpected_error, R.string.error_development_server_io, e);
            }

        } catch (UserFriendlyException e) {
            listener.onConnectionError(e);
        } finally {
            listener.onConnectorDeath();
        }
    }

}
