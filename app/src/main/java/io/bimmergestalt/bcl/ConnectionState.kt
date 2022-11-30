package io.bimmergestalt.bcl

interface ConnectionState {
    val transportState: TransportState
    val bclState: BclState
    val proxyState: ProxyState

    enum class TransportState {
        WAITING,
        SEARCHING,
        OPENING,
        ACTIVE,
        FAILED
    }
    enum class BclState {
        WAITING,
        OPENING,
        FAILED,
        INITIALIZING,
        NEGOTIATING,
        ACTIVE,
        SHUTDOWN;

        fun asBclReport(): String {
            return when(this) {
                WAITING -> "UNKNOWN"
                OPENING -> "SESSION_INIT_BYTES_SEND"
                INITIALIZING -> "GOT_HANDSHAKE"
                NEGOTIATING -> "SELECT_PROTOCOL"
                FAILED -> "HANDSHAKE_FAILED"
                ACTIVE -> "WORKING"
                SHUTDOWN -> "DETACHED"
            }
        }
    }
    enum class ProxyState {
        WAITING,
        ACTIVE,
        FAILED
    }
}

interface MutableConnectionState: ConnectionState {
    override var transportState: ConnectionState.TransportState
    override var bclState: ConnectionState.BclState
    override var proxyState: ConnectionState.ProxyState
}

data class ConnectionStateConcrete(
    override var transportState: ConnectionState.TransportState,
    override var bclState: ConnectionState.BclState,
    override var proxyState: ConnectionState.ProxyState
): ConnectionState {
    companion object {
        val WAITING = ConnectionStateConcrete(
            ConnectionState.TransportState.WAITING,
            ConnectionState.BclState.WAITING,
            ConnectionState.ProxyState.WAITING
        )
    }
}