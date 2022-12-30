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
class BclClientTransport(input: InputStream, output: OutputStream, val connectionState: MutableConnectionState,
						 val destProtocolFactories: Iterable<DestProtocolFactory>) {
	companion object {
		private const val SESSION_INIT_WAIT = 1000L
	}

	@Suppress("UnstableApiUsage")
	private val input = CountingInputStream(input)
	private val output = CountingOutputStream(output)
	private val packetOutput = BclPacketSender(output)
	@Suppress("UnstableApiUsage")
	val bytesRead: Long
		get() = input.count
	val bytesWritten: Long
		get() = output.count

	var running = true
	var state: ConnectionState.BclState
		private set(value) { connectionState.bclState = value }
		get() = connectionState.bclState
	val isConnected: Boolean
		get() = connectionHandshake != null
	private var connectionHandshake: BclPacket.Specialized.Handshake? = null
	var startupTimestamp = 0L
	val instanceId: Int
		get() = connectionHandshake?.instanceId?.toInt() ?: -1

	val destProtocols: MutableList<Protocol> = ArrayList()
	val openConnections: MutableMap<Pair<Short, Short>, ProxyClientConnection> = HashMap()

	@Throws(IOException::class)
	fun connect() {
		state = ConnectionState.BclState.OPENING
		waitForHandshake()
		selectProtocol()
		state = ConnectionState.BclState.ACTIVE
		openProtocols()
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
			packetOutput.write(BclPacket.Specialized.SelectProto(3.toShort()))
			doKnock()
		}
	}

	private fun doKnock() {
		// i think empty values work fine here
		packetOutput.write(BclPacket.Specialized.Knock(
			ByteArray(0), ByteArray(0),
			ByteArray(0), ByteArray(0),
			0 /*A4A*/, 1
		))
		// 0 and 1
		// otherwise 1 /*TouchCommand*/ and 7
	}

	private fun openProtocols() {
		destProtocols.add(WatchdogProtocol.Factory().onConnect(
			this,
			BclClientConnectionOpener()
		))
		destProtocolFactories.forEach { factory ->
			try {
				destProtocols.add(factory.onConnect(this, BclClientConnectionOpener()))
			} catch (e: Exception) {
				shutdown()
				throw IOException("Failed to initialize protocol $factory")
			}
		}
	}

	inner class BclClientConnectionOpener: ProxyConnectionOpener {
		override fun openConnection(
			srcPort: Short,
			destPort: Short,
			client: OutputStream
		): ProxyClientConnection {
			val connection = synchronized(openConnections) {
				val key = Pair(srcPort, destPort)
				Logger.info {"Opening BCL Connection $srcPort:$destPort"}
				if (openConnections.containsKey(key)) {
					throw IllegalArgumentException("Duplicate to/from connection")
				}
				val connection = ProxyClientConnection(
					srcPort,
					destPort,
					client,
					BclOutputStream(srcPort, destPort, packetOutput)
				) {
					closeConnection(it)
				}

				openConnections[key] = connection
				connection
			}
			packetOutput.write(BclPacket.Specialized.Open(srcPort, destPort))
			return connection
		}

		fun closeConnection(connection: ProxyClientConnection) {
			Logger.info {"Closing BCL Connection ${connection.srcPort}:${connection.destPort}"}
			synchronized(openConnections) { openConnections.remove(connection.key) }
		}
	}

	fun run() {
		while (running) {
			readPacket()
		}
	}

	private fun readPacket() {
		val packet = BclPacket.readFrom(input)
		packetOutput.write(BclPacket.Specialized.DataAck(8 + packet.data.size))

		if (packet.dest == 5001.toShort() || (packet.command != BclPacket.COMMAND.DATA && packet.command != BclPacket.COMMAND.DATAACK)) {
			Logger.info {"Received support packet $packet"}
		}
		if (packet.command == BclPacket.COMMAND.DATA) {
			val key = Pair(packet.src, packet.dest)
			val connection = synchronized(openConnections) { openConnections[key] }
			if (connection == null) {
				// manually send, which normally the connection.close() handles
				packetOutput.write(BclPacket.Specialized.Close(packet.src, packet.dest))
			}
			try {
				connection?.toClient?.write(packet.data)
			} catch (e: IOException) {
				synchronized(openConnections) { openConnections.remove(key)?.close() }
			}
		}
		else if (packet.command == BclPacket.COMMAND.CLOSE) {
			val key = Pair(packet.src, packet.dest)
			val connection = synchronized(openConnections) { openConnections[key] }
			connection?.toClient?.close()
		}
		else if (packet.command == BclPacket.COMMAND.HANGUP) {
			shutdown()
		}
	}

	fun getReport() = BclConnectionReport(
		startupTimestamp,
		bytesRead, bytesWritten,
		openConnections.size,
		instanceId.toShort(),
		0,
		connectionHandshake?.bufferSize ?: -1,
		0,
		state.asBclReport()
	)

	fun shutdown() {
		if (running) {
			running = false
			try {
				destProtocols.forEach { protocol ->
					protocol.shutdown()
				}
				openConnections.values.toMutableList().forEach {
					it.close()
				}
				packetOutput.write(BclPacket(BclPacket.COMMAND.HANGUP, 0, 0, ByteArray(0)))
				output.flush()
			} finally {
				state = ConnectionState.BclState.SHUTDOWN
			}
		}
	}
}