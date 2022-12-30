package io.bimmergestalt.bcl.protocols

import io.bimmergestalt.bcl.client.BclClientTransport
import io.bimmergestalt.bcl.client.ProxyConnectionOpener
import java.net.ConnectException


interface Protocol {
    fun shutdown()
}
interface DestProtocolFactory {
    @Throws(ConnectException::class)
    fun onConnect(transport: BclClientTransport, opener: ProxyConnectionOpener): Protocol
}