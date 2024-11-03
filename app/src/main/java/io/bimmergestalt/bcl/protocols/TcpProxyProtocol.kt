package io.bimmergestalt.bcl.protocols

import io.bimmergestalt.bcl.BclProxyServer
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.MutableConnectionState
import io.bimmergestalt.bcl.client.ProxyConnectionGrantor
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import org.tinylog.kotlin.Logger
import java.io.IOException
import kotlin.concurrent.thread

class TcpProxyProtocol(val listenPort: Short, destPort: Short,
                       bclProxy: ProxyConnectionOpener,
                       val state: MutableConnectionState, val onStateUpdate: (ConnectionState.ProxyState) -> Unit
): Protocol {

    private val proxyConnectionGrantor = ProxyConnectionGrantor(destPort, bclProxy)
    private val proxyServer = BclProxyServer(listenPort, proxyConnectionGrantor)
    private val proxyThread = thread(start = false, isDaemon = true) {
        try {
            this.proxyServer.run()
        } catch (e: IOException) {
            Logger.warn(e) { "IOException while running EtchProxyService" }
        }
    }

    init {
        startProxy()
    }

    @Throws(IOException::class)
    private fun startProxy() {
        try {
            proxyServer.listen()
        } catch (e: IOException) {
            state.proxyState = ConnectionState.ProxyState.FAILED
            onStateUpdate(state.proxyState)
            throw e
        }
        state.proxyState = ConnectionState.ProxyState.ACTIVE
        onStateUpdate(state.proxyState)
        proxyThread.start()
    }

    override fun shutdown() {
        proxyServer.shutdown()
        proxyThread.interrupt()
        state.proxyState = ConnectionState.ProxyState.WAITING
        onStateUpdate(state.proxyState)
    }
}