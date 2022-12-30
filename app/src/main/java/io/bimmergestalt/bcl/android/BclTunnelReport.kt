package io.bimmergestalt.bcl.android

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import io.bimmergestalt.bcl.protocols.DestProtocolFactory
import io.bimmergestalt.bcl.protocols.Protocol

class BclTunnelReport(private val context: Context, private val transport: BclClientTransport, private val brand: String): Protocol {
    class Factory(private val context: Context, private val brand: String): DestProtocolFactory {
        override fun onConnect(
            transport: BclClientTransport,
            opener: ProxyConnectionOpener
        ): Protocol {
            return BclTunnelReport(context, transport, brand)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val updateDebugIntent = Runnable {
        val report = transport.getReport()
        val startTimestamp = SystemClock.uptimeMillis() - (System.currentTimeMillis() - report.startTimestamp)
        val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_INFO")
            .putExtra("EXTRA_START_TIMESTAMP", startTimestamp)
            .putExtra("EXTRA_NUM_BYTES_READ", report.bytesRead)
            .putExtra("EXTRA_NUM_BYTES_WRITTEN", report.bytesWritten)
            .putExtra("EXTRA_NUM_CONNECTIONS", report.numConnections)
            .putExtra("EXTRA_INSTANCE_ID", report.instanceId)
            .putExtra("EXTRA_WATCHDOG_RTT", report.watchdogRtt)
            .putExtra("EXTRA_HU_BUFFER_SIZE", report.huBufSize)
            .putExtra("EXTRA_REMAINING_ACK_BYTES", report.remainingAckBytes)
            .putExtra("EXTRA_STATE", report.state)
            .putExtra("EXTRA_BRAND", brand)
        context.sendBroadcast(intent)
        scheduleUpdateDebugIntent()
    }

    init {
        scheduleUpdateDebugIntent()
    }

    private fun scheduleUpdateDebugIntent() {
        handler.removeCallbacks(updateDebugIntent)
        handler.postDelayed(updateDebugIntent, 500)
    }

    override fun shutdown() {
        handler.post {
            handler.removeCallbacks(updateDebugIntent)
        }
    }
}