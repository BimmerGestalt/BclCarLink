package io.bimmergestalt.bclclient.models

import android.content.Context
import android.graphics.Color
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.android.BtClientService
import io.bimmergestalt.bclclient.R
import io.bimmergestalt.bclclient.helpers.ConnectionStateStrings.toStringResource
import io.bimmergestalt.bclclient.helpers.LiveDataHelpers.map

class ConnectionStatusViewModel: ViewModel() {
    val btConnectionStatus = BtClientService.state

    val btTransportIsConnecting: LiveData<Boolean> = btConnectionStatus.map {
        it.transportState == ConnectionState.TransportState.OPENING
    }
    val btTransportIcon: LiveData<Int> = btConnectionStatus.map {
        if (it.transportState == ConnectionState.TransportState.FAILED) {
            R.drawable.ic_baseline_wifi_off_24
        } else {
            R.drawable.ic_baseline_wifi_24
        }
    }
    val btTransportColor: LiveData<Int> = btConnectionStatus.map {
        when (it.transportState) {
            ConnectionState.TransportState.FAILED ->  Color.RED
            ConnectionState.TransportState.ACTIVE -> Color.GREEN
            else -> Color.GRAY
        }
    }
    val btTransportText: LiveData<Context.() -> String> = btConnectionStatus.map {
        { it.transportState.toStringResource(resources) }
    }

    val bclIsConnecting: LiveData<Boolean> = btConnectionStatus.map {
        val connectingStates = listOf(
            ConnectionState.BclState.OPENING,
            ConnectionState.BclState.INITIALIZING,
            ConnectionState.BclState.NEGOTIATING
        )
        connectingStates.contains(it.bclState)
    }
    val bclIcon: Int = R.drawable.ic_car_sports

    val bclColor: LiveData<Int> = btConnectionStatus.map {
        when (it.bclState) {
            ConnectionState.BclState.FAILED -> Color.RED
            ConnectionState.BclState.INITIALIZING -> Color.GREEN
            ConnectionState.BclState.NEGOTIATING -> Color.GREEN
            ConnectionState.BclState.ACTIVE -> Color.GREEN
            else ->  Color.GRAY
        }
    }
    val bclText: LiveData<Context.() -> String> = btConnectionStatus.map {
        { it.bclState.toStringResource(resources) }
    }

    val proxyIcon: Int = R.drawable.ic_baseline_widgets_24

    val proxyColor: LiveData<Int> = btConnectionStatus.map {
        when (it.proxyState) {
            ConnectionState.ProxyState.WAITING -> Color.GRAY
            ConnectionState.ProxyState.ACTIVE -> Color.GREEN
            ConnectionState.ProxyState.FAILED -> Color.RED
        }
    }
    val proxyText: LiveData<Context.() -> String> = btConnectionStatus.map {
        { it.proxyState.toStringResource(resources) }
    }
}