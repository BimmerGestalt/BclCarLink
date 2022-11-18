package io.bimmergestalt.bcl.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.bimmergestalt.bcl.BclConnection
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.util.*

/**
 * Connect to a given Bluetooth Device and attempt to open the serial port
 *
 * Will shut down upon failure
 */
class BtConnection(val device: BluetoothDevice, val onConnect: (BclConnection) -> Unit): Thread() {

	companion object {
		val SDP_BCL: UUID =
			UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")      // Generic Serial Port
	}

	private var socket: BluetoothSocket? = null
	var bclConnection: BclConnection? = null
		private set
	val isConnected: Boolean
		get() = bclConnection?.isConnected == true

	override fun run() {
		val socket = try {
			device.createRfcommSocketToServiceRecord(SDP_BCL)
		} catch (_: SecurityException) {
			return
		}
		this.socket = socket

		try {
			connectSocket()
		} catch (_: SecurityException) {
			return
		} catch (_: InterruptedException) {
			return
		}

		try {
			if (socket.isConnected) {
				val bclConnection = BclConnection(socket.inputStream, socket.outputStream)
				this.bclConnection = bclConnection
				bclConnection.connect()
				bclConnection.doWatchdog()
				onConnect(bclConnection)
				bclConnection.run()
			}
		} catch (_: SecurityException) {
		} catch (e: IOException) {
			Logger.warn(e) { "IOException communicating BCL" }
		} catch (_: InterruptedException) {}
	}

	fun connectSocket() {
		var count = 0
		val maxTries = 10
		while (socket?.isConnected == false && count < maxTries) {
			try {
				socket?.connect()
			} catch (e: SecurityException) {
				throw e
			} catch (e: IOException) {
				Logger.warn(e) { "IOException opening BluetoothSocket, trying again ($count<$maxTries tries)" }
				sleep(2000)
				count += 1
			}
		}
	}

	fun shutdown() {
		bclConnection?.tryShutdown()
		socket?.close()
		interrupt()
	}
}