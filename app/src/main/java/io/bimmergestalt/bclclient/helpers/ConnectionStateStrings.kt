package io.bimmergestalt.bclclient.helpers

import android.content.res.Resources
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bclclient.R

object ConnectionStateStrings {
    fun ConnectionState.TransportState.toStringResource(resources: Resources): String {
        return when (this) {
            ConnectionState.TransportState.WAITING -> resources.getString(R.string.status_transport_waiting)
            ConnectionState.TransportState.SEARCHING -> resources.getString(R.string.status_transport_searching)
            ConnectionState.TransportState.OPENING -> resources.getString(R.string.status_transport_opening)
            ConnectionState.TransportState.ACTIVE -> resources.getString(R.string.status_transport_active)
            ConnectionState.TransportState.FAILED -> resources.getString(R.string.status_transport_failed)
        }
    }

    fun ConnectionState.BclState.toStringResource(resources: Resources): String {
        return when (this) {
            ConnectionState.BclState.WAITING -> resources.getString(R.string.status_bcl_waiting)
            ConnectionState.BclState.OPENING -> resources.getString(R.string.status_bcl_opening)
            ConnectionState.BclState.INITIALIZING -> resources.getString(R.string.status_bcl_initializing)
            ConnectionState.BclState.NEGOTIATING -> resources.getString(R.string.status_bcl_negotiating)
            ConnectionState.BclState.ACTIVE -> resources.getString(R.string.status_bcl_active)
            ConnectionState.BclState.FAILED -> resources.getString(R.string.status_bcl_failed)
            ConnectionState.BclState.SHUTDOWN -> resources.getString(R.string.status_bcl_shutdown)
        }
    }

    fun ConnectionState.ProxyState.toStringResource(resources: Resources): String {
        return when (this) {
            ConnectionState.ProxyState.WAITING -> resources.getString(R.string.status_proxy_waiting)
            ConnectionState.ProxyState.ACTIVE -> resources.getString(R.string.status_proxy_active)
            ConnectionState.ProxyState.FAILED -> resources.getString(R.string.status_proxy_failed)
        }
    }
}