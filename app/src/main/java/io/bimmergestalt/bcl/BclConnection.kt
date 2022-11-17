package io.bimmergestalt.bcl

import com.google.common.io.CountingInputStream
import com.google.common.io.CountingOutputStream
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException

/**
 * Runs the BCL protocol over the given socket streams
 */
class BclConnection(input: InputStream, output: OutputStream) {
	companion object {
		/**
		 * The BCL states, which are announced out to other apps on the phone
		 */
		enum class STATE {
			UNKNOWN,
			SESSION_INIT_BYTES_SEND,
			HANDSHAKE_FAILED,
			GOT_HANDSHAKE,
		}

		private const val SESSION_INIT_WAIT = 1000L
	}

	@Suppress("UnstableApiUsage")
	private val input = CountingInputStream(input)
	private val output = CountingOutputStream(output)

	@Suppress("UnstableApiUsage")
	val bytesRead: Long
		get() = input.count
	val bytesWritten: Long
		get() = output.count

	var state: STATE = STATE.UNKNOWN
		private set
	private var connectionHandshake: BclPacket.Specialized.Handshake? = null
	var startupTimestamp = 0L

	@Throws(IOException::class)
	fun connect() {
		state = STATE.SESSION_INIT_BYTES_SEND
		waitForHandshake()
		selectProtocol()
	}

	private fun waitForHandshake() {
		while (connectionHandshake == null) {
			initSession()
			if (input.available() >= 16) {
				readHandshake()
			} else {
				Thread.sleep(SESSION_INIT_WAIT)
			}
		}
		startupTimestamp = System.currentTimeMillis()
	}

	private fun initSession() {
		output.write(0x12)
		output.write(0x34)
		output.write(0x56)
		output.write(0x78)
		Thread.sleep(SESSION_INIT_WAIT)
	}

	private fun readHandshake() {
		val packet = BclPacket.readFrom(input)
		if (packet !is BclPacket.Specialized.Handshake) {
			state = STATE.HANDSHAKE_FAILED
			throw ConnectException("Did not receive BCL handshake")
		}
		connectionHandshake = packet
		state = STATE.GOT_HANDSHAKE
	}

	private fun selectProtocol() {
		val connectionHandshake = connectionHandshake ?: throw AssertionError("No Handshake Receiver")
		val version = connectionHandshake.version.toByte()
		if (version > 3) {
			BclPacket.Specialized.SelectProto(version.toShort()).writeTo(output)
			doKnock()
		}
	}

	private fun doKnock() {
		// i think empty values work fine here
		BclPacket.Specialized.Knock(ByteArray(0), ByteArray(0),
			ByteArray(0), ByteArray(0),
			0 /*A4A*/, 1).writeTo(output)
	}

	fun doWatchdog() {
		val data = byteArrayOf(0x13, 0x37, 0x13, 0x37)
		BclPacket(BclPacket.COMMAND.DATA, 5001, 5001, data).writeTo(output)
	}

	fun run() {
		while (true) {
			val packet = BclPacket.readFrom(input)
			Logger.info { "Received packet $packet" }
		}
	}

	fun tryShutdown() {
		try {
			shutdown()
		} catch (_: IOException) {}
	}
	fun shutdown() {
		BclPacket(BclPacket.COMMAND.HANGUP, 0, 0, ByteArray(0))
	}
}