package io.bimmergestalt.bcl.client

import io.bimmergestalt.bcl.protocols.Protocol
import io.bimmergestalt.bcl.protocols.ProxyClientConnection
import java.io.OutputStream

class ProxyConnectionGrantor(val destPort: Short, val opener: ProxyConnectionOpener): Protocol {
    private val clients = HashMap<Short, ProxyClientConnection>()
    private var nextSocketIndex: Short = 10

    fun openConnection(toTcpClient: OutputStream): ProxyClientConnection {
        return synchronized(this) {
            val connection = opener.openConnection(nextSocketIndex, destPort, toTcpClient)
            clients[nextSocketIndex] = connection
            nextSocketIndex = (nextSocketIndex + 1).toShort()
            connection
        }
    }

    override fun shutdown() {
        synchronized(this) {
            clients.values.forEach {
                it.close()
            }
            clients.clear()
            nextSocketIndex = 10
        }
    }
}