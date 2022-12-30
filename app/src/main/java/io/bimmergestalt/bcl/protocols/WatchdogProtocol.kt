package io.bimmergestalt.bcl.protocols

import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import org.tinylog.Logger
import java.io.OutputStream
import kotlin.concurrent.thread

class WatchdogProtocol(val transport: BclClientTransport, val connection: ProxyConnectionOpener,
                       private val interval: Int = WATCHDOG_INTERVAL, private val timeout: Int = WATCHDOG_TIMEOUT): Protocol {
    companion object {
        const val WATCHDOG_PORT: Short = 5001
        val WATCHDOG_DATA = byteArrayOf(0x13, 0x37, 0x13, 0x37)
        const val WATCHDOG_INTERVAL = 5000
        const val WATCHDOG_TIMEOUT = 20000
    }

    class Factory: DestProtocolFactory {
        override fun onConnect(
            transport: BclClientTransport,
            opener: ProxyConnectionOpener
        ): Protocol {
            return WatchdogProtocol(transport, opener)
        }
    }

    val watchdogListener = object: OutputStream() {
        override fun write(b: Int) {
            // maybe track RTT?
            Logger.debug { "Received Watchdog pong"}
            lastWatchdogPong = System.currentTimeMillis()
        }

        override fun close() {
            transport.shutdown()
        }
    }
    val watchdogConnection = connection.openConnection(WATCHDOG_PORT, WATCHDOG_PORT, watchdogListener)
    var lastWatchdogPong = System.currentTimeMillis()
    val watchdogTimer = thread(start = true, isDaemon = true) {
        try {
            while (true) {
                Thread.sleep(interval.toLong())
                watchdogConnection.toTunnel.write(WATCHDOG_DATA)
                if (lastWatchdogPong + timeout < System.currentTimeMillis()) {
                    Logger.info {"Shutting down from dead watchdog after ${timeout/1000} seconds"}
                    transport.shutdown()
                }
            }
        } catch (_: InterruptedException) {}
    }

    override fun shutdown() {
        watchdogConnection.close()
        watchdogTimer.interrupt()
    }
}