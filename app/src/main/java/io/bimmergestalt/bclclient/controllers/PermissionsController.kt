package io.bimmergestalt.bclclient.controllers

import android.Manifest
import android.app.Activity
import androidx.core.app.ActivityCompat

class PermissionsController(val activity: Activity) {
    companion object {
        const val REQUEST_BLUETOOTH = 50
    }
    fun promptBluetoothConnect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH)
        }
    }
}