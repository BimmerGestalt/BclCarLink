package io.bimmergestalt.bcl

import io.bimmergestalt.bcl.client.ProxyConnectionGrantor
import io.bimmergestalt.bcl.protocols.ProxyClientConnection
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class BclProxyServer(val listenPort: Short, val connection: ProxyConnectionGrantor) {
    val selector = Selector.open()
    val serverSocket = ServerSocketChannel.open()
    val connections = HashMap<SocketChannel, ProxyClientConnection>()

    fun listen() {
        serverSocket.configureBlocking(false)
        serverSocket.register(selector, SelectionKey.OP_ACCEPT)
        serverSocket.bind(InetSocketAddress(listenPort.toInt()))
    }

    fun run() {
        val inputBuffer = ByteBuffer.allocate(4000)
        while (serverSocket.isOpen) {
            selector.select(10000)
            val readyKeys = selector.selectedKeys()
            readyKeys.forEach { key ->
                try {
                    if (key.isAcceptable) {
                        // new client connection
                        val socket = serverSocket.accept()
                        socket.configureBlocking(false)
                        socket.socket().tcpNoDelay = true
                        socket.register(selector, SelectionKey.OP_READ)
                        val bclProxyConnection = connection.openConnection(SocketChannelOutputStream(socket))
                        connections[socket] = bclProxyConnection
                    }
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
        shutdown()
    }

    fun shutdown() {
        synchronized(connections) {
            connections.keys.forEach {
                try {
                    it.close()
                } catch (e: IOException) {
                    Logger.warn(e) { "IOException while shutting down BclProxy client" }
                }
            }
            connections.clear()
            try {
                serverSocket.close()
            } catch (e: IOException) {
                Logger.warn(e) { "IOException while shutting down BclProxy ServerSocket" }
            }
        }
    }
}

/**
 * Masquerades a SocketChannel as an OutputStream
 */
class SocketChannelOutputStream(private val channel: SocketChannel): OutputStream() {
    override fun write(b: Int) {
        channel.write(ByteBuffer.wrap(byteArrayOf(b.toByte())))
    }

    override fun write(b: ByteArray?) {
        b ?: return
        channel.write(ByteBuffer.wrap(b))
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        b ?: return
        channel.write(ByteBuffer.wrap(b, off, len))
    }

    override fun close() {
        channel.close()
    }
}