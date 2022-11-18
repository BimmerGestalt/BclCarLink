package io.bimmergestalt.bcl

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

class BclProxyServer(val listenPort: Int, val destPort: Int, val connection: BclConnection) {
    val selector = Selector.open()
    val serverSocket = ServerSocketChannel.open()
    val sockets = HashMap<SocketChannel, OutputStream>()

    fun listen() {
        val inputBuffer = ByteBuffer.allocate(4000)
        serverSocket.configureBlocking(false)
        serverSocket.register(selector, SelectionKey.OP_ACCEPT)
        serverSocket.bind(InetSocketAddress(listenPort))
        while (serverSocket.isOpen) {
            selector.select(2500)
            val readyKeys = selector.selectedKeys()
            readyKeys.forEach { key ->
                try {
                    if (key.isAcceptable) {
                        // new client connection
                        val socket = serverSocket.accept()
                        socket.configureBlocking(false)
                        socket.socket().tcpNoDelay = false
                        socket.register(selector, SelectionKey.OP_READ)
                        val bclOutput = connection.openSocket(
                            destPort.toShort(),
                            socket
                        )
                        sockets[socket] = bclOutput
                    }
                    if (key.isReadable) {
                        // new data from the client
                        val channel = key.channel()
                        if (channel is SocketChannel) {
                            val len = channel.read(inputBuffer)
                            Logger.debug { "Read $len bytes from client socket" }
                            if (len > 0) {
                                try {
                                    sockets[channel]?.write(inputBuffer.array(), 0, len)
                                } catch (e: IOException) {
                                    Logger.warn(e) { "IOException while writing to BclConnection from client" }
                                    channel.close()
                                    sockets.remove(channel)
                                }
                            } else if (len == -1) {
                                key.cancel()
                                sockets[channel]?.close()
                                sockets.remove(channel)
                            }
                            inputBuffer.clear()
                        }
                    }
                } catch (_: CancelledKeyException) {}
            }
            readyKeys.clear()
            connection.doWatchdog() // TODO move watchdog to a better place
        }
        shutdown()
    }

    fun shutdown() {
        synchronized(sockets) {
            sockets.keys.forEach {
                try {
                    it.close()
                } catch (e: IOException) {
                    Logger.warn(e) { "IOException while shutting down BclProxy client" }
                }
            }
            sockets.clear()
            try {
                serverSocket.close()
            } catch (e: IOException) {
                Logger.warn(e) { "IOException while shutting down BclProxy ServerSocket" }
            }
        }
    }
}