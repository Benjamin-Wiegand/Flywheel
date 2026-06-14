package io.benwiegand.projection.geargrinder;

import static android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED;
import static android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.CompanionDeviceService;
import android.companion.DevicePresenceEvent;
import android.content.Intent;
import android.net.MacAddress;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.S)
public class BluetoothCompanionService extends CompanionDeviceService {
    private static final String TAG = BluetoothCompanionService.class.getSimpleName();

    private void connectBluetooth(String address) {
        Log.i(TAG, "connecting bluetooth to " + address + " for wireless auto-start");
        startService(new Intent(this, ConnectionService.class)
                .setAction(ConnectionService.INTENT_ACTION_CONNECT_BLUETOOTH)
                .putExtra(ConnectionService.INTENT_EXTRA_BLUETOOTH_DEVICE_ADDRESS, address));
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private void connectBluetooth(AssociationInfo associationInfo) {
        MacAddress macAddress = associationInfo.getDeviceMacAddress();
        if (macAddress == null) {
            Log.e(TAG, "mac address is null");
            return;
        }

        connectBluetooth(macAddress.toString());
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    private AssociationInfo getAssociationById(CompanionDeviceManager deviceManager, int id) {
        for (AssociationInfo associationInfo : deviceManager.getMyAssociations()) {
            if (associationInfo.getId() != id) continue;
            return associationInfo;
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onDeviceAppeared(@NonNull AssociationInfo associationInfo) {
        Log.i(TAG, "device appeared: " + associationInfo);
        connectBluetooth(associationInfo);
    }

    @Override
    public void onDeviceAppeared(@NonNull String address) {
        Log.i(TAG, "device appeared: " + address);
        connectBluetooth(address);
    }

    @RequiresApi(api = Build.VERSION_CODES.BAKLAVA)
    @Override
    public void onDevicePresenceEvent(@NonNull DevicePresenceEvent event) {
        Log.i(TAG, "device presence event: " + event);
        switch (event.getEvent()) {
            case EVENT_BLE_APPEARED, EVENT_BT_CONNECTED -> {
                CompanionDeviceManager deviceManager = getSystemService(CompanionDeviceManager.class);
                AssociationInfo associationInfo = getAssociationById(deviceManager, event.getAssociationId());
                if (associationInfo == null) {
                    Log.wtf(TAG, "unable to find association");
                    return;
                }

                connectBluetooth(associationInfo);
            }
        }

    }
}
