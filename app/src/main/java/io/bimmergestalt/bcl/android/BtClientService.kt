package io.bimmergestalt.bcl.android

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.ArrayMap
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LiveData
import io.bimmergestalt.bcl.ConnectionState
import org.tinylog.kotlin.Logger
import java.util.*

fun BluetoothDevice?.isCar(): Boolean {
	return this?.safeName?.startsWith("BMW") == true || this?.safeName?.startsWith("MINI") == true
}
fun BluetoothDevice?.hasBCL(): Boolean {
	return this?.safeUuids?.any {
		it == BtConnection.SDP_BCL
	} ?: false
}
val BluetoothDevice?.safeName: String?
	get() = try { this?.name } catch (_: SecurityException) { this?.address }
val BluetoothDevice?.safeUuids: List<UUID>?
	get() = try { this?.uuids?.map { it.uuid } } catch (_: SecurityException) { null }
val BluetoothDevice?.brand: String?
	get() = Regex("^[a-zA-Z]+").matchAt(safeName ?: "", 0)?.value?.lowercase()

val BluetoothProfile.safeConnectedDevices: List<BluetoothDevice>
	get() {
		return try {
			this.connectedDevices
		} catch (e: SecurityException) {
			// missing BLUETOOTH_CONNECT permission
			emptyList()
		}
	}


class BtClientService: Service() {

	companion object {
		private val _state = ConnectionStateLiveData()
		val state: LiveData<ConnectionState> = _state
		private const val INTERVAL_SCAN: Int = 2000
		private const val INTERVAL_KEEPALIVE: Int = 10000
		private const val ETCH_PROXY_PORT: Short = 4007
		private const val ETCH_DEST_PORT: Short = 4004
		val ACTION_SHUTDOWN = "io.bimmergestalt.bcl.android.BcClientService.SHUTDOWN"
	}

	// on startup, check for Bluetooth Connect privilege, stop self if not
	// prune any disconnected sockets
	// scan all the Adapters for car devices
	// create connections if not already connected
	// socket connection is blocking for up to 12s timeout
	// each socket needs its own Thread

	private val handler = Handler(Looper.getMainLooper())
	private val intervalConnector = Runnable {
		tryConnections()
		scheduleScan()
	}
	private var subscribed = false
	private val a2dpListener = ProfileListener(BluetoothProfile.A2DP) { tryConnections() }
	private val btThreads = ArrayMap<String, BtConnection>()      // bt address to thread
	private val uuidListener = object: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
			Logger.info { "Received UUID response from ${device?.safeName}: ${device?.safeUuids}" }
			tryConnections()
		}
	}

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!hasBluetoothPermission()) {
			Logger.warn { "Bluetooth permission is missing, not scanning" }
			stopSelf(startId)
			return START_NOT_STICKY
		}
		if (intent?.action != ACTION_SHUTDOWN) {
			scanBluetoothDevices()
		} else {
			stopSelf()
		}
		return START_STICKY
	}

	private fun hasBluetoothPermission(): Boolean {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			if (PermissionChecker.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PermissionChecker.PERMISSION_GRANTED) {
				return true
			}
		} else {
			if (PermissionChecker.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PermissionChecker.PERMISSION_GRANTED) {
				return true
			}
		}
		return false
	}

	private fun scheduleScan() {
		handler.removeCallbacks(intervalConnector)
		if (_state.transportState == ConnectionState.TransportState.ACTIVE) {
			handler.postDelayed(intervalConnector, INTERVAL_KEEPALIVE.toLong())
		} else {
			handler.postDelayed(intervalConnector, INTERVAL_SCAN.toLong())
		}
	}

	private fun scanBluetoothDevices() {
		if (!subscribed) {
			try {
				Logger.debug { "Checking Bluetooth State ${_state.transportState}" }
				if (_state.transportState == ConnectionState.TransportState.WAITING) {
					_state.transportState = ConnectionState.TransportState.SEARCHING
				}
				this.getSystemService(BluetoothManager::class.java).adapter.getProfileProxy(this, a2dpListener, BluetoothProfile.A2DP)
			} catch (_: SecurityException) {
				_state.transportState = ConnectionState.TransportState.FAILED
			}

			val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
			registerReceiver(uuidListener, filter)

			scheduleScan()
			subscribed = true
		} else {
			tryConnections()
		}
		a2dpListener.fetchUuidsWithSdp()
	}

	private fun tryConnections() {
		synchronized(btThreads) {
			val connectedDevices = a2dpListener.profile?.safeConnectedDevices ?: emptyList()
			connectedDevices.filter { it.hasBCL() }.forEach {
				if (btThreads[it.address]?.isAlive != true) {
					Logger.info { "Starting to connect to ${it.safeName}" }
					btThreads[it.address] = tryConnection(it).apply {
						start()
					}
				} else {
					Logger.info { "Still connected to ${it.safeName}" }
				}
			}
			(btThreads.keys - connectedDevices.map { it.address }.toSet()).forEach { address ->
				Logger.info { "Shutting down thread from disconnected $address" }
				btThreads.remove(address)?.shutdown()
			}
		}
	}

	private fun tryConnection(device: BluetoothDevice): BtConnection {
		return BtConnection(device, _state, listOf(
			BclTunnelReport.Factory(this, device.brand ?: "bmw"),
			EtchProxyService.Factory(this, ETCH_PROXY_PORT, ETCH_DEST_PORT,
				device.brand ?: "bmw", _state)
		))
	}

	private fun stopScan() {
		if (subscribed) {
			try {
				val adapter = this.getSystemService(BluetoothManager::class.java).adapter
				adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpListener.profile)
				adapter.cancelDiscovery()
			} catch (_: SecurityException) { }
			unregisterReceiver(uuidListener)
			_state.transportState = ConnectionState.TransportState.WAITING
		}
		subscribed = false
		handler.removeCallbacks(intervalConnector)
	}

	private fun disconnect() {
		Logger.info {"Shutting down BtClientService"}
		synchronized(btThreads) {
			btThreads.values.forEach {
				it?.shutdown()
			}
			btThreads.clear()
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		stopScan()
		disconnect()
	}

	class ProfileListener(profileId: Int, var callback: () -> Unit): BluetoothProfile.ServiceListener {
		var profile: BluetoothProfile? = null
		private val profileName = when(profileId) {
			BluetoothProfile.HEADSET -> "hf"
			BluetoothProfile.A2DP -> "a2dp"
			else -> "Profile#$profileId"
		}

		override fun onServiceDisconnected(p0: Int)
		{
			profile = null
			Logger.debug { "$profileName watcher is unloaded" }
			callback()
		}
		override fun onServiceConnected(p0: Int, profile: BluetoothProfile?) {
			this.profile = profile
			Logger.debug { "$profileName watcher is loaded" }
			fetchUuidsWithSdp()
			callback()
		}

		fun fetchUuidsWithSdp() {
			val cars = profile?.safeConnectedDevices?.filter { it.isCar() } ?: listOf()
			cars.forEach {
				try {
					it.fetchUuidsWithSdp()
				} catch (_: SecurityException) {}
			}
		}
	}
}