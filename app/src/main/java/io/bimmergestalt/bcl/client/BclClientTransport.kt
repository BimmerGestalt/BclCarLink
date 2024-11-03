package io.bimmergestalt.bcl.client

import com.google.common.io.CountingInputStream
import com.google.common.io.CountingOutputStream
import io.bimmergestalt.bcl.*
import io.bimmergestalt.bcl.ByteArrayExt.decodeHex
import io.bimmergestalt.bcl.protocols.*
import org.tinylog.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Runs the BCL protocol over the given socket streams
 */
class BclClientTransport(input: InputStream, output: OutputStream, val connectionState: MutableConnectionState): BclPacketSender {
	companion object {
		private const val SESSION_INIT_WAIT = 1000L
	}

	@Suppress("UnstableApiUsage")
	private val input = CountingInputStream(input)
	private val output = CountingOutputStream(output)
	private val packetOutput = BclPacketSenderConcrete(output)

	@Suppress("UnstableApiUsage")
	val bytesRead: Long
		get() = input.count
	val bytesWritten: Long
		get() = output.count
	var openConnectionCount: Int = 0

	var state: ConnectionState.BclState
		private set(value) { connectionState.bclState = value }
		get() = connectionState.bclState
	val isConnected: Boolean
		get() = connectionHandshake != null
	private var connectionHandshake: BclPacket.Specialized.Handshake? = null
	var startupTimestamp = 0L
	val instanceId: Int
		get() = connectionHandshake?.instanceId?.toInt() ?: -1

	@Throws(IOException::class)
	fun connect() {
		state = ConnectionState.BclState.OPENING
		waitForHandshake()
		selectProtocol()
		state = ConnectionState.BclState.ACTIVE
	}

	private fun waitForHandshake() {
		while (connectionHandshake == null) {
			if (input.available() >= 16) {
				// throws if incorrect handshake packet
				readHandshake()
			} else {
				initSession()
				Thread.sleep(SESSION_INIT_WAIT)
			}
		}
		startupTimestamp = System.currentTimeMillis()
	}

	private fun initSession() {
		output.write("12345678".decodeHex())
		Thread.sleep(SESSION_INIT_WAIT)
	}

	private fun readHandshake() {
		val packet = BclPacket.readFrom(input)
		if (packet !is BclPacket.Specialized.Handshake) {
			state = ConnectionState.BclState.FAILED
			throw IOException("Did not receive BCL handshake")
		}
		connectionHandshake = packet
		state = ConnectionState.BclState.INITIALIZING
	}

	private fun selectProtocol() {
		val connectionHandshake = connectionHandshake ?: throw AssertionError("No Handshake Received")
		state = ConnectionState.BclState.NEGOTIATING
		val version = connectionHandshake.version.toByte()
		if (version > 3) {
			// hardcoded to version 3, version 4 has an unknown watchdog behavior
			packetOutput.writePacket(BclPacket.Specialized.SelectProto(3.toShort()))
			doKnock()
		}
	}

	private fun doKnock() {
		// i think empty values work fine here
		packetOutput.writePacket(BclPacket.Specialized.Knock(
			ByteArray(0), ByteArray(0),
			ByteArray(0), ByteArray(0),
			0 /*A4A*/, 1
		))
		// 0 and 1
		// otherwise 1 /*TouchCommand*/ and 7
	}

	fun readPacket(): BclPacket? {
		val packet = BclPacket.readFrom(input)
		packetOutput.writePacket(BclPacket.Specialized.DataAck(8 + packet.data.size))

		if (packet.dest == 5001.toShort() || (packet.command != BclPacket.COMMAND.DATA && packet.command != BclPacket.COMMAND.DATAACK)) {
			Logger.info {"Received support packet $packet"}
		}
		when (packet.command) {
			BclPacket.COMMAND.DATA -> return packet
			BclPacket.COMMAND.CLOSE -> return packet
			BclPacket.COMMAND.HANGUP -> shutdown()
			else -> Logger.warn {"Received unhandled packet $packet"}
		}
		return null
	}

	override fun writePacket(packet: BclPacket) {
		packetOutput.writePacket(packet)
	}

	fun getReport() = BclConnectionReport(
		startupTimestamp,
		bytesRead, bytesWritten,
		openConnectionCount,
		instanceId.toShort(),
		0,
		connectionHandshake?.bufferSize ?: -1,
		0,
		state.asBclReport()
	)

	fun shutdown() {
		try {
			packetOutput.writePacket(BclPacket(BclPacket.COMMAND.HANGUP, 0, 0, ByteArray(0)))
			output.flush()
			state = ConnectionState.BclState.SHUTDOWN
		} catch (e: Exception) {
			Logger.warn(e) {"Error while shutting down BCL"}
		}
	}
}