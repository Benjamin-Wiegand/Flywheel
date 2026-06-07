package io.benwiegand.projection.geargrinder.connector;

import android.content.Context;

import androidx.annotation.StringRes;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.channel.ControlChannel;
import io.benwiegand.projection.geargrinder.crypto.CryptoManager;
import io.benwiegand.projection.geargrinder.crypto.KeystoreManager;
import io.benwiegand.projection.geargrinder.crypto.LGTMTrustManager;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.exception.CorruptedCertificateException;
import io.benwiegand.projection.geargrinder.exception.CorruptedKeyException;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.protocol.AAConstants;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.transfer.AATransferInterface;

public abstract class AAConnector {

    public interface StateListener {
        void onConnectingStatus(@StringRes int status);
        void onConnected();
        void onConnectionError(UserFriendlyException e);
        void onDisconnected();
        void onConnectorDeath();
    }

    protected final Context context;
    protected final StateListener listener;
    private final ControlListener controlListener;
    private final ConnectionService.ServiceBinder connectionServiceBinder;
    private final SettingsManager settingsManager;

    private final CryptoManager cryptoManager;

    public AAConnector(Context context, StateListener listener, ControlListener controlListener, ConnectionService.ServiceBinder connectionServiceBinder, SettingsManager settingsManager) {
        this.context = context;
        this.listener = listener;
        this.controlListener = controlListener;
        this.connectionServiceBinder = connectionServiceBinder;
        this.settingsManager = settingsManager;

        cryptoManager = new CryptoManager(context);
    }

    public abstract void stop();

    public abstract void start();

    protected TLSService createTlsService() throws CorruptedKeyException, CorruptedCertificateException {
        KeystoreManager keystoreManager = cryptoManager.getKeystoreForCurrentConfiguration();
        TrustManager[] trustManagers = new TrustManager[] {new LGTMTrustManager()};
        KeyManager[] keyManagers = keystoreManager.getKeyManagers();
        return new TLSService(trustManagers, keyManagers);
    }

    protected void connectionLoop(AATransferInterface transferInterface) throws CorruptedKeyException, CorruptedCertificateException {
        TLSService tlsService = createTlsService();
        MessageBroker messageBroker = new MessageBroker(transferInterface, tlsService);
        ControlChannel controlChannel = new ControlChannel(context, messageBroker, tlsService, controlListener, settingsManager, connectionServiceBinder);
        try {
            messageBroker.registerForChannel(AAConstants.CHANNEL_CONTROL, controlChannel);
            messageBroker.loop();
        } finally {
            controlChannel.destroy();
            messageBroker.destroy();
        }
    }

}
