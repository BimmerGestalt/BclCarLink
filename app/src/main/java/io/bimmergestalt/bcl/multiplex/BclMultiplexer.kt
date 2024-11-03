package io.bimmergestalt.bcl.multiplex

import io.bimmergestalt.bcl.BclOutputStream
import io.bimmergestalt.bcl.BclPacket
import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import io.bimmergestalt.bcl.protocols.DestProtocolFactory
import io.bimmergestalt.bcl.protocols.Protocol
import io.bimmergestalt.bcl.protocols.ProxyClientConnection
import io.bimmergestalt.bcl.protocols.WatchdogProtocol
import org.tinylog.Logger
import java.io.IOException
import java.io.OutputStream

class BclMultiplexer(val bclTransport: BclClientTransport,
                     val destProtocolFactories: Iterable<DestProtocolFactory>) {

    var running = true

    val destProtocols: MutableList<Protocol> = ArrayList()
    val openConnections: MutableMap<Pair<Short, Short>, ProxyClientConnection> = HashMap()

    fun openProtocols() {
        destProtocols.add(
            WatchdogProtocol.Factory().onConnect(
            bclTransport,
            BclClientConnectionOpener()
        ))
        destProtocolFactories.forEach { factory ->
            try {
                destProtocols.add(factory.onConnect(bclTransport, BclClientConnectionOpener()))
            } catch (e: Exception) {
                bclTransport.shutdown()
                throw IOException("Failed to initialize protocol $factory")
            }
        }
    }

    fun run() {
        while (running) {
            val packet = bclTransport.readPacket()
            if (packet != null) {
                handlePacket(packet)
            }
        }
    }

    private fun handlePacket(packet: BclPacket) {
        if (packet.command == BclPacket.COMMAND.DATA) {
            val key = Pair(packet.src, packet.dest)
            val connection = synchronized(openConnections) { openConnections[key] }
            if (connection == null) {
                // manually send, which normally the connection.close() handles
                bclTransport.writePacket(BclPacket.Specialized.Close(packet.src, packet.dest))
            }
            try {
                connection?.toClient?.write(packet.data)
            } catch (e: IOException) {
                synchronized(openConnections) {
                    openConnections.remove(key)?.close()
                    bclTransport.openConnectionCount = openConnections.size
                }
            }
        }
        else if (packet.command == BclPacket.COMMAND.CLOSE) {
            val key = Pair(packet.src, packet.dest)
            val connection = synchronized(openConnections) { openConnections[key] }
            connection?.toClient?.close()
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
                    BclOutputStream(srcPort, destPort, bclTransport)
                ) {
                    closeConnection(it)
                }

                openConnections[key] = connection
                bclTransport.openConnectionCount = openConnections.size
                connection
            }
            bclTransport.writePacket(BclPacket.Specialized.Open(srcPort, destPort))
            return connection
        }

        fun closeConnection(connection: ProxyClientConnection) {
            Logger.info {"Closing BCL Connection ${connection.srcPort}:${connection.destPort}"}
            synchronized(openConnections) {
                openConnections.remove(connection.key)
                bclTransport.openConnectionCount = openConnections.size
            }
        }
    }

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
                bclTransport.shutdown()
            } catch (e: Exception) {
                Logger.warn(e) { "Error while shutting down BclMultiplexer" }
            }
        }
    }
}