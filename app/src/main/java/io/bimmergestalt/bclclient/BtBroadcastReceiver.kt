package io.bimmergestalt.bclclient

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.SparseArray
import io.bimmergestalt.bcl.android.BtClientService
import io.bimmergestalt.bcl.android.isCar
import io.bimmergestalt.bcl.android.safeName
import org.tinylog.Logger

class BtBroadcastReceiver: BroadcastReceiver() {
    val states = SparseArray<String>(4).apply {
        put(BluetoothProfile.STATE_DISCONNECTED, "disconnected")
        put(BluetoothProfile.STATE_CONNECTING, "connecting")
        put(BluetoothProfile.STATE_CONNECTED, "connected")
        put(BluetoothProfile.STATE_DISCONNECTING, "disconnecting")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (!MainService.shouldStartAutomatically(context)) {
            return
        }
        if (intent.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            val oldState = states[intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)] ?: "unknown"
            val newStateCode = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            val newState = states[intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)] ?: "unknown"
            Logger.info { "Notified of A2DP status: ${device?.safeName} $oldState -> $newState" }
            if (newStateCode == BluetoothProfile.STATE_CONNECTED && device?.isCar() == true) {
                MainService.startService(context)
            }
        }
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            Logger.info {"Notified of ACL connection: ${device?.safeName}" }
            if (device?.isCar() == true) {
                MainService.startService(context)
            }
        }
    }
}