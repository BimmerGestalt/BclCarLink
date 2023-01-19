package io.bimmergestalt.bclclient.models

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PermissionsViewModel: ViewModel() {
    private val _supportsBluetoothConnectPermission = MutableLiveData(false)
    val supportsBluetoothConnectPermission: LiveData<Boolean> = _supportsBluetoothConnectPermission
    private val _hasBluetoothConnectPermission = MutableLiveData(false)
    val hasBluetoothConnectPermission: LiveData<Boolean> = _hasBluetoothConnectPermission

    fun update(context: Context) {
        _supportsBluetoothConnectPermission.value = android.os.Build.VERSION.SDK_INT >= 31
        _hasBluetoothConnectPermission.value = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}