package io.bimmergestalt.bcl.android

import androidx.lifecycle.LiveData
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.MutableConnectionState

class ConnectionStateLiveData: MutableConnectionState, LiveData<ConnectionState>() {

    override var transportState: ConnectionState.TransportState = ConnectionState.TransportState.WAITING
        set(value) {field = value; postState()}
    override var bclState: ConnectionState.BclState = ConnectionState.BclState.WAITING
        set(value) {field = value; postState()}
    override var proxyState: ConnectionState.ProxyState = ConnectionState.ProxyState.WAITING
        set(value) {field = value; postState()}

    private fun postState() {
        postValue(this)
    }

    init {
        postState()
    }

    override fun toString(): String {
        return "ConnectionStateLiveData(transportState=$transportState, bclState=$bclState, proxyState=$proxyState)"
    }
}