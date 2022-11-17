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
class BtConnection(val device: BluetoothDevice): Thread() {

	companion object {
		val SDP_BCL: UUID =
			UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")      // Generic Serial Port
	}

	var socket: BluetoothSocket? = null
	var bclConnection: BclConnection? = null

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
				bclConnection.run()
			}
		} catch (_: SecurityException) {
		} catch (e: IOException) {
			Logger.warn(e) { "IOException communicating BCL" }
		} catch (_: InterruptedException) {}
	}

	fun connectSocket() {
		while (socket?.isConnected == false) {
			try {
				socket?.connect()
			} catch (e: SecurityException) {
				throw e
			} catch (e: IOException) {
				Logger.warn(e) { "IOException opening BluetoothSocket, trying again" }
				sleep(1000)
			}
		}
	}

	fun shutdown() {
		bclConnection?.tryShutdown()
		socket?.close()
		interrupt()
	}
}