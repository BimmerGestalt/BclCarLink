package io.bimmergestalt.bcl.client

import io.bimmergestalt.bcl.protocols.ProxyClientConnection
import java.io.OutputStream

interface ProxyConnectionOpener {
    fun openConnection(srcPort: Short, destPort: Short, client: OutputStream): ProxyClientConnection
}