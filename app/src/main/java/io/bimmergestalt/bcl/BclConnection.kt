package io.bimmergestalt.bcl

import com.google.common.io.CountingInputStream
import com.google.common.io.CountingOutputStream
import io.bimmergestalt.bcl.ByteArrayExt.decodeHex
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

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

	var running = true
	var state: STATE = STATE.UNKNOWN
		private set
	val isConnected: Boolean
		get() = connectionHandshake != null
	private var connectionHandshake: BclPacket.Specialized.Handshake? = null
	var startupTimestamp = 0L
	var lastWatchdog = 0L

	val clients = HashMap<Short, SocketChannel>()
	var nextSocketIndex: Short = 10

	@Throws(IOException::class)
	fun connect() {
		state = STATE.SESSION_INIT_BYTES_SEND
		waitForHandshake()
		selectProtocol()
		openWatchdog()
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
		output.write("12345678".decodeHex())
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
		// 0 and 1
		// otherwise 1 /*TouchCommand*/ and 7
	}

	private fun openWatchdog() {
		BclPacket.Specialized.Open(5001, 5001).writeTo(output)
	}

	fun doWatchdog() {
		if (lastWatchdog + 2000 < System.currentTimeMillis()) {
			val data = byteArrayOf(0x13, 0x37, 0x13, 0x37)
			BclPacket.Specialized.Data(5001, 5001, data).writeTo(output)
			lastWatchdog = System.currentTimeMillis()
		}
	}

	fun openSocket(destPort: Short, client: SocketChannel): OutputStream {
		val src = synchronized(clients) {
			val src = nextSocketIndex
			nextSocketIndex = nextSocketIndex.inc()
			clients[src] = client
			src
		}
		BclPacket.Specialized.Open(src, destPort).writeTo(output)
		return BclOutputStream(src, destPort, output)
	}

	fun run() {
		while (running) {
			val packet = BclPacket.readFrom(input)
			if (packet.command == BclPacket.COMMAND.DATA && packet.dest == 5001.toShort()) {
				BclPacket.Specialized.DataAck(8 + packet.data.size).writeTo(output)
				doWatchdog()
			}
			else if (packet.command == BclPacket.COMMAND.DATA) {
				BclPacket.Specialized.DataAck(8 + packet.data.size).writeTo(output)
				synchronized(clients) {
					val channel = clients[packet.src]
					if (channel == null) {
						BclPacket.Specialized.Close(packet.src, packet.dest).writeTo(output)
					}
					try {
						channel?.write(ByteBuffer.wrap(packet.data))
					} catch (e: IOException) {
						clients.remove(packet.src)
						BclPacket.Specialized.Close(packet.src, packet.dest).writeTo(output)
					}
				}
			}
		}
	}

	fun tryShutdown() {
		try {
			shutdown()
		} catch (_: IOException) {}
	}
	fun shutdown() {
		running = false
		BclPacket.Specialized.Close(5001, 5001).writeTo(output)
		clients.keys.forEach {
			BclPacket.Specialized.Close(it, 4004).writeTo(output)		// TODO track dest port to close correctly
		}
		BclPacket(BclPacket.COMMAND.HANGUP, 0, 0, ByteArray(0)).writeTo(output)
	}
}