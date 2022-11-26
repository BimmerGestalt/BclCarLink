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
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import io.bimmergestalt.bcl.BclConnection
import io.bimmergestalt.bcl.BclProxyManager
import io.bimmergestalt.bcl.ConnectionState
import org.tinylog.kotlin.Logger
import java.io.IOException
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
		private const val ETCH_PROXY_PORT = 4007
		private const val ETCH_DEST_PORT = 4004

		fun shouldStartAutomatically(context: Context): Boolean {
			val preferences = PreferenceManager.getDefaultSharedPreferences(context)
			return preferences.getBoolean("automatic_bt_connection", true)
		}
		fun startService(context: Context) {
			try {
				context.startService(Intent(context, BtClientService::class.java))
			} catch (e: IllegalStateException) {
				Logger.warn { "Unable to start BtClientService: $e"}
			}
		}
		fun stopService(context: Context) {
			try {
				context.startService(Intent(context, BtClientService::class.java).setAction("disconnect"))
			} catch (_: IllegalStateException) {}
		}
	}

	// on startup, check for Bluetooth Connect privilege, stop self if not
	// prune any disconnected sockets
	// scan all the Adapters for car devices
	// create connections if not already connected
	// socket connection is blocking for up to 12s timeout
	// each socket needs its own Thread

	private val handler = Handler(Looper.getMainLooper())
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
	private var bclProxy = BclProxyManager(ETCH_PROXY_PORT, ETCH_DEST_PORT, _state)

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (!hasBluetoothPermission()) {
			Logger.warn { "Bluetooth permission is missing, not scanning" }
			stopSelf(startId)
			return START_NOT_STICKY
		}
		if (intent?.action != "disconnect") {
			scanBluetoothDevices()
		} else {
			stopScan()
			disconnect()
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

	private fun scanBluetoothDevices() {
		if (!subscribed) {
			try {
				if (_state.transportState == ConnectionState.TransportState.WAITING) {
					_state.transportState = ConnectionState.TransportState.SEARCHING
				}
				this.getSystemService(BluetoothManager::class.java).adapter.getProfileProxy(this, a2dpListener, BluetoothProfile.A2DP)
			} catch (_: SecurityException) {
				_state.transportState = ConnectionState.TransportState.FAILED
			}

			val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
			registerReceiver(uuidListener, filter)

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
					val btConnection = BtConnection(it, _state) {
						applyProxy()
					}
					btThreads[it.address] = btConnection.apply {
						start()
					}
				} else {
					Logger.info { "Still connected to ${it.safeName}" }
				}
			}
			(btThreads.keys - connectedDevices.map { it.address }.toSet()).forEach { address ->
				Logger.info { "Shutting down thread from disconnected $address" }
				btThreads[address]?.shutdown()
				btThreads.remove(address)
			}
			applyProxy()
		}
	}

	private fun applyProxy() {
		val btConnection = synchronized(btThreads) {
			btThreads.values.firstOrNull { it.isAlive && it.isConnected }
		}
		val bclConnection = btConnection?.bclConnection
		if (bclConnection != null) {
			Logger.info { "Starting BCL Proxy"}
			try {
				bclProxy.startProxy(bclConnection)

				announceProxy(btConnection.device.brand ?: "bmw", bclConnection.instanceId)
				scheduleUpdateDebugIntent()
			} catch (e: IOException) {
				Logger.warn(e) { "Failure while starting BCL Proxy" }
			}
		} else if (bclProxy.proxyServer != null) {
			Logger.info { "Stopping BCL Proxy"}
			bclProxy.shutdown()
			unannounceProxy()
		}
	}

	private fun announceProxy(brand: String, instanceId: Int) {
		val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_ATTACHED")
			.putExtra("EXTRA_BRAND", brand)
			.putExtra("EXTRA_ACCESSORY_BRAND", brand)
			.putExtra("EXTRA_HOST", "127.0.0.1")
			.putExtra("EXTRA_PORT", ETCH_PROXY_PORT)
			.putExtra("EXTRA_INSTANCE_ID", instanceId)
		sendBroadcast(intent)
		val transport = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_TRANSPORT_SWITCH")
			.putExtra("EXTRA_TRANSPORT", "BT")
		sendBroadcast(transport)
	}

	private fun unannounceProxy() {
		val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_DETACHED")
		sendBroadcast(intent)
	}

	private fun stopScan() {
		if (subscribed) {
			try {
				val adapter = this.getSystemService(BluetoothManager::class.java).adapter
				adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dpListener.profile)
				adapter.cancelDiscovery()
			} catch (_: SecurityException) { }
			unregisterReceiver(uuidListener)
		}
		subscribed = false
	}

	private fun disconnect() {
		btThreads.values.forEach {
			it?.shutdown()
		}
		bclProxy.shutdown()
	}

	override fun onDestroy() {
		super.onDestroy()
		stopScan()
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
			Logger.debug { "$profileName is unloaded" }
			callback()
		}
		override fun onServiceConnected(p0: Int, profile: BluetoothProfile?) {
			this.profile = profile
			Logger.debug { "$profileName is loaded" }
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

	private val updateDebugIntent = Runnable {
		val connection = btThreads.values.firstOrNull { it.isAlive && it.isConnected }
		val bclConnection = connection?.bclConnection
		if (bclConnection != null) {
			val report = bclConnection.getReport()
			val startTimestamp = SystemClock.uptimeMillis() - (System.currentTimeMillis() - report.startTimestamp)
			val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_INFO")
				.putExtra("EXTRA_START_TIMESTAMP", startTimestamp)
				.putExtra("EXTRA_NUM_BYTES_READ", report.bytesRead)
				.putExtra("EXTRA_NUM_BYTES_WRITTEN", report.bytesWritten)
				.putExtra("EXTRA_NUM_CONNECTIONS", report.numConnections)
				.putExtra("EXTRA_INSTANCE_ID", report.instanceId)
				.putExtra("EXTRA_WATCHDOG_RTT", report.watchdogRtt)
				.putExtra("EXTRA_HU_BUFFER_SIZE", report.huBufSize)
				.putExtra("EXTRA_REMAINING_ACK_BYTES", report.remainingAckBytes)
				.putExtra("EXTRA_STATE", report.state)
				.putExtra("EXTRA_BRAND", connection.device.brand ?: "bmw")
			sendBroadcast(intent)
			scheduleUpdateDebugIntent()
		}
	}
	private fun scheduleUpdateDebugIntent() {
		handler.removeCallbacks(updateDebugIntent)
		handler.postDelayed(updateDebugIntent, 500)
	}
}