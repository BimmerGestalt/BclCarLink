package io.bimmergestalt.bcl

import org.tinylog.kotlin.Logger
import java.io.IOException
import kotlin.concurrent.thread

class BclProxyManager(val port: Int, val destPort: Int) {
    var proxyThread: Thread? = null
    var proxyServer: BclProxyServer? = null

    fun startProxy(bclConnection: BclConnection) {
        shutdown()
        val proxyServer = BclProxyServer(port, destPort, bclConnection)
        this.proxyServer = proxyServer
        proxyThread = thread(start = true, isDaemon = true) {
            try {
                proxyServer.listen()
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
    }
}