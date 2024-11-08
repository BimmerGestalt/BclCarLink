package io.bimmergestalt.bcl.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.MutableConnectionState
import io.bimmergestalt.bcl.multiplex.BclMultiplexer
import io.bimmergestalt.bcl.protocols.DestProtocolFactory
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.util.*

/**
 * Connect to a given Bluetooth Device and attempt to open the serial port
 *
 * Will shut down upon failure
 */
class BtConnection(val device: BluetoothDevice, val connectionState: MutableConnectionState,
				   val destProtocolFactories: Iterable<DestProtocolFactory>): Thread() {

	companion object {
		val SDP_BCL: UUID =
			UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")      // Generic Serial Port
	}

	private var socket: BluetoothSocket? = null
	var bclConnection: BclClientTransport? = null
		private set
	var bclMultiplexer: BclMultiplexer? = null
		private set
	val isConnected: Boolean
		get() = bclConnection?.isConnected == true

	override fun run() {
		connectionState.transportState = ConnectionState.TransportState.OPENING
		val socket = try {
			device.createRfcommSocketToServiceRecord(SDP_BCL)
		} catch (_: SecurityException) {
			return
		}
		this.socket = socket

		try {
			connectSocket(socket)
			connectBcl(socket)
		} catch (_: SecurityException) {
			return
		} catch (_: InterruptedException) {
			return
		}
	}

	private fun connectSocket(socket: BluetoothSocket) {
		var count = 0
		val maxTries = 10
		while (!socket.isConnected && count < maxTries) {
			connectionState.transportState = ConnectionState.TransportState.OPENING
			try {
				socket.connect()
			} catch (e: SecurityException) {
				throw e
			} catch (e: IOException) {
				connectionState.transportState = ConnectionState.TransportState.FAILED
				Logger.warn(e) { "IOException opening BluetoothSocket, trying again ($count<$maxTries tries)" }
				sleep(2000)
				count += 1
			}
		}
	}

	private fun connectBcl(socket: BluetoothSocket) {
		while (socket.isConnected) {
			try {
				connectionState.transportState = ConnectionState.TransportState.ACTIVE
				val bclConnection = BclClientTransport(socket.inputStream, socket.outputStream, connectionState)
				this.bclConnection = bclConnection
				bclConnection.connect()
				val bclMultiplexer = BclMultiplexer(bclConnection, destProtocolFactories)
				this.bclMultiplexer = bclMultiplexer
				bclMultiplexer.openProtocols()
				bclMultiplexer.run()
			} catch (_: SecurityException) {
			} catch (e: IOException) {
				Logger.warn(e) { "IOException communicating BCL" }
			} catch (_: InterruptedException) {
			} finally {
				bclMultiplexer?.shutdown()
				bclMultiplexer = null
				bclConnection?.shutdown()
				bclConnection = null
			}
			connectionState.transportState = ConnectionState.TransportState.WAITING
			sleep(1000)
		}
	}

	fun shutdown() {
		bclConnection?.shutdown()
		socket?.close()
		interrupt()
	}
}