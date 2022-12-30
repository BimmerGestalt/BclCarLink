package io.bimmergestalt.bcl.android

import android.content.Context
import android.content.Intent
import io.bimmergestalt.bcl.BclProxyServer
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.MutableConnectionState
import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import io.bimmergestalt.bcl.client.ProxyConnectionGrantor
import io.bimmergestalt.bcl.protocols.DestProtocolFactory
import io.bimmergestalt.bcl.protocols.Protocol
import org.tinylog.kotlin.Logger
import java.io.IOException
import kotlin.concurrent.thread

class EtchProxyService(val context: Context, val listenPort: Short, destPort: Short,
                       brand: String, instanceId: Int, bclProxy: ProxyConnectionOpener,
                       val state: MutableConnectionState): Protocol {
    class Factory(val context: Context, val listenPort: Short, val destPort: Short, val brand: String, val state: MutableConnectionState): DestProtocolFactory {
        override fun onConnect(
            transport: BclClientTransport,
            opener: ProxyConnectionOpener
        ): Protocol {
            return EtchProxyService(context, listenPort, destPort, brand, transport.instanceId, opener, state)
        }
    }

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
        announceProxy(brand, instanceId)
    }

    @Throws(IOException::class)
    private fun startProxy() {
        try {
            proxyServer.listen()
        } catch (e: IOException) {
            state.proxyState = ConnectionState.ProxyState.FAILED
            throw e
        }
        state.proxyState = ConnectionState.ProxyState.ACTIVE
        proxyThread.start()
    }

    private fun announceProxy(brand: String, instanceId: Int) {
        val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_ATTACHED")
            .putExtra("EXTRA_BRAND", brand)
            .putExtra("EXTRA_ACCESSORY_BRAND", brand)
            .putExtra("EXTRA_HOST", "127.0.0.1")
            .putExtra("EXTRA_PORT", listenPort)
            .putExtra("EXTRA_INSTANCE_ID", instanceId)
        context.sendBroadcast(intent)
        val transport = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_TRANSPORT_SWITCH")
            .putExtra("EXTRA_TRANSPORT", "BT")
        context.sendBroadcast(transport)
    }

    private fun unannounceProxy() {
        val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_DETACHED")
        context.sendBroadcast(intent)
    }

    override fun shutdown() {
        unannounceProxy()
        proxyServer.shutdown()
        proxyThread.interrupt()
        state.proxyState = ConnectionState.ProxyState.WAITING
    }
}