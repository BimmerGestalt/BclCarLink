package io.bimmergestalt.bcl.multiplex

import io.bimmergestalt.bcl.protocols.ProxyClientConnection
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class TcpMuxer(val selector: Selector) {
    val connections = HashMap<SocketChannel, ProxyClientConnection>()
    fun addChannel(channel: SocketChannel, muxStream: ProxyClientConnection) {
        channel.configureBlocking(false)
        channel.socket().tcpNoDelay = true
        channel.register(selector, SelectionKey.OP_READ)
        connections[channel] = muxStream
    }

    fun process() {
        val inputBuffer = ByteBuffer.allocate(4000)

        selector.selectNow()
        val readyKeys = selector.selectedKeys()
        readyKeys.forEach { key ->
            try {
                if (key.isReadable) {
                    // new data from the client
                    val channel = key.channel()
                    if (channel is SocketChannel) {
                        val len = channel.read(inputBuffer)
//                            Logger.debug { "Read $len bytes from client socket" }
                        if (len > 0) {
                            try {
                                connections[channel]?.toTunnel?.write(inputBuffer.array(), 0, len)
                            } catch (e: IOException) {
                                Logger.warn(e) { "IOException while writing to BclConnection from client" }
                                channel.close()
                                connections.remove(channel)?.close()
                            }
                        } else if (len == -1) {
                            key.cancel()
                            connections.remove(channel)?.close()
                        }
                        inputBuffer.clear()
                    }
                }
            } catch (_: CancelledKeyException) {}
        }
        readyKeys.clear()
    }

    fun shutdown() {
        synchronized(connections) {
            connections.keys.forEach {
                try {
                    it.close()
                } catch (e: IOException) {
                    Logger.warn(e) { "IOException while shutting down muxer client" }
                }
            }
            connections.clear()
        }
    }
}