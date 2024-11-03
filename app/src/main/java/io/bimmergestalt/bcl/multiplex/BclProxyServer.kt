package io.bimmergestalt.bcl.multiplex

import io.bimmergestalt.bcl.client.ProxyConnectionGrantor
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
    val muxer = TcpMuxer(selector)

    fun listen() {
        serverSocket.configureBlocking(false)
        serverSocket.register(selector, SelectionKey.OP_ACCEPT)
        serverSocket.bind(InetSocketAddress(listenPort.toInt()))
    }

    fun run() {
        while (serverSocket.isOpen) {
            selector.select(10000)
            val readyKeys = selector.selectedKeys()
            readyKeys.forEach { key ->
                try {
                    if (key.isAcceptable) {
                        // new client connection
                        val socket: SocketChannel = serverSocket.accept()
                        val bclProxyConnection = connection.openConnection(SocketChannelOutputStream(socket))
                        muxer.addChannel(socket, bclProxyConnection)
                    }
                } catch (_: CancelledKeyException) {}
            }
            readyKeys.clear()

            muxer.process()
        }
        shutdown()
    }

    fun shutdown() {
        try {
            serverSocket.close()
        } catch (e: IOException) {
            Logger.warn(e) { "IOException while shutting down BclProxy ServerSocket" }
        }
        muxer.shutdown()
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