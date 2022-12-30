package io.bimmergestalt.bcl.protocols

import java.io.OutputStream

class ProxyClientConnection(val srcPort: Short, val destPort: Short,
                            val toClient: OutputStream, val toTunnel: OutputStream, val onClose: (ProxyClientConnection) -> Unit) {
    val key: Pair<Short, Short> = Pair(srcPort, destPort)

    fun close() {
        toClient.close()
        toTunnel.close()
        onClose(this)
    }
}