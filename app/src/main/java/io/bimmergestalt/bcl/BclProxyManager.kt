package io.bimmergestalt.bcl

import org.tinylog.kotlin.Logger
import java.io.IOException
import kotlin.concurrent.thread

class BclProxyManager(val port: Int, val destPort: Int, val state: MutableConnectionState) {
    var proxyThread: Thread? = null
        private set
    var proxyServer: BclProxyServer? = null
        private set

    @Throws(IOException::class)
    fun startProxy(bclConnection: BclConnection) {
        shutdown()
        try {
            val proxyServer = BclProxyServer(port, destPort, bclConnection)
            this.proxyServer = proxyServer
            proxyServer.listen()
        } catch (e: IOException) {
            state.proxyState = ConnectionState.ProxyState.FAILED
            throw e
        }
        state.proxyState = ConnectionState.ProxyState.ACTIVE
        proxyThread = thread(start = true, isDaemon = true) {
            try {
                this.proxyServer?.run()
            } catch (e: IOException) {
                Logger.warn(e) { "IOException while running BclProxyServer" }
            }
        }
    }

    fun shutdown() {
        proxyServer?.shutdown()
        proxyServer = null
        proxyThread?.interrupt()
        proxyThread = null
        state.proxyState = ConnectionState.ProxyState.WAITING
    }
}