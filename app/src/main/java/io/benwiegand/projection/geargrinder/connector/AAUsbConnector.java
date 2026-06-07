package io.benwiegand.projection.geargrinder.connector;

import android.content.Context;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import io.benwiegand.projection.geargrinder.ConnectionService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.message.AAFrame;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;
import io.benwiegand.projection.geargrinder.transfer.UsbTransferInterface;

public class AAUsbConnector extends AAConnector {
    private static final String TAG = AAUsbConnector.class.getSimpleName();
    private static final Set<String> TARGET_ACCESSORY_MODELS = Set.of("Android Auto", "Android Open Automotive Protocol");

    private final Thread thread = new Thread(this::connect, "Geargrinder USB connection loop");
    private final UsbManager usbManager;

    public AAUsbConnector(Context context, StateListener listener, ControlListener controlListener, ConnectionService.ServiceBinder connectionServiceBinder, SettingsManager settingsManager) {
        super(context, listener, controlListener, connectionServiceBinder, settingsManager);
        usbManager = context.getSystemService(UsbManager.class);
    }

    @Override
    public void stop() {
        thread.interrupt();
    }

    @Override
    public void start() {
        thread.start();
    }

    private UsbAccessory findUsbHeadunit() {
        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories == null) {
            Log.e(TAG, "no accessories");
            return null;
        }

        for (UsbAccessory usbAccessory : accessories){
            Log.d(TAG, "accessory: " + usbAccessory);
            if (!TARGET_ACCESSORY_MODELS.contains(usbAccessory.getModel())) continue;
            return usbAccessory;
        }

        Log.w(TAG, "no usb headunit found");
        return null;
    }

    private void connect() {
        try {
            UsbAccessory headunit = findUsbHeadunit();
            if (headunit == null) {
                Log.e(TAG, "no headunit found");
                throw new UserFriendlyException(context, R.string.car_connection_error, R.string.error_no_usb_headunit);
            }

            if (!usbManager.hasPermission(headunit)) {
                Log.e(TAG, "no permission for usb accessory");
                throw new UserFriendlyException(context, R.string.car_connection_error, R.string.error_grant_usb_permission);
            }

            Log.i(TAG, "headunit found");
            listener.onConnectingStatus(R.string.connecting_to_car);

            // TODO: open accessory more efficiently (see openAccessory()
            try (ParcelFileDescriptor pfd = usbManager.openAccessory(headunit);
                 FileInputStream is = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream os = new FileOutputStream(pfd.getFileDescriptor())) {

                Log.d(TAG, "opened usb file descriptor [" + pfd.getFd() + "]: " + pfd);
                listener.onConnected();

                Log.d(TAG, "starting services");
                UsbTransferInterface usbTransferInterface = new UsbTransferInterface(pfd, is, os, AAFrame.MAX_LENGTH);
                connectionLoop(usbTransferInterface);
                listener.onDisconnected();
            } catch (IOException e) {
                throw new UserFriendlyException(context, R.string.car_connection_unexpected_error, R.string.error_car_io_usb_generic, e);
            }

        } catch (UserFriendlyException e) {
            listener.onConnectionError(e);
        } finally {
            listener.onConnectorDeath();
        }
    }
}
