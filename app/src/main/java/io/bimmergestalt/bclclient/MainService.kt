package io.bimmergestalt.bclclient

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager
import io.bimmergestalt.bcl.ConnectionState
import io.bimmergestalt.bcl.ConnectionStateConcrete
import io.bimmergestalt.bcl.android.BtClientService
import io.bimmergestalt.bclclient.helpers.ConnectionStateStrings.toStringResource
import org.tinylog.kotlin.Logger

class MainService: LifecycleService() {

    companion object {
        const val ONGOING_NOTIFICATION_ID = 20503
        const val NOTIFICATION_CHANNEL_ID = "ConnectionNotification"
        const val ACTION_SHUTDOWN = "io.bimmergestalt.bclclient.MainService.SHUTDOWN"
        const val CONNECTED_SEARCHING_TIMEOUT: Long = 2 * 60 * 1000     // if Bluetooth is connected
        const val DISCONNECTED_SEARCHING_TIMEOUT: Long = 20 * 1000      // if Bluetooth is not connected

        fun shouldStartAutomatically(context: Context): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            return preferences.getBoolean("automatic_bt_connection", true)
        }
        fun startService(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, MainService::class.java))
                } else {
                    context.startService(Intent(context, MainService::class.java))
                }
            } catch (e: IllegalStateException) {
                Logger.warn { "Unable to start MainService: $e"}
            }
        }
        fun stopService(context: Context) {
            try {
                context.startService(Intent(context, BtClientService::class.java).setAction(ACTION_SHUTDOWN))
            } catch (_: IllegalStateException) {}
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val btServiceConnection by lazy {
        object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) { }
            override fun onServiceDisconnected(name: ComponentName?) { }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startBluetoothService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHUTDOWN) {
            stopSelf()
        } else {
            Logger.info { "Starting MainService" }
            createNotificationChannel()
            startServiceNotification(BtClientService.state.value ?: ConnectionStateConcrete.WAITING)
            scheduleShutdownTimeout()
            subscribeConnections(BtClientService.state)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun startBluetoothService() {
        val intent = Intent(applicationContext, BtClientService::class.java)
        bindService(intent, btServiceConnection, BIND_AUTO_CREATE)
        startService(intent)
    }

    val shutdownTimeout = Runnable {
        if (BtClientService.state.value?.transportState == ConnectionState.TransportState.SEARCHING) {
            stopSelf()
        }
    }
    fun scheduleShutdownTimeout() {
        val timeout = if (BtClientService.state.value?.transportState == ConnectionState.TransportState.SEARCHING) {
            DISCONNECTED_SEARCHING_TIMEOUT
        } else {
            CONNECTED_SEARCHING_TIMEOUT
        }
        handler.removeCallbacks(shutdownTimeout)
        handler.postDelayed(shutdownTimeout, timeout)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_connection),
                NotificationManager.IMPORTANCE_MIN)

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startServiceNotification(btState: ConnectionState) {
        val notifyIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val shutdownIntent = Intent(applicationContext, MainService::class.java).apply {
            action = ACTION_SHUTDOWN
        }
        val shutdownAction = NotificationCompat.Action.Builder(null, getString(R.string.service_shutdown),
            PendingIntent.getService(this, 40, shutdownIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        ).build()
        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.status_transport_waiting))       // will be replaced in the next step
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(PendingIntent.getActivity(applicationContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(shutdownAction)

        if (btState.bclState == ConnectionState.BclState.ACTIVE) {
            notificationBuilder.setContentText(btState.proxyState.toStringResource(resources))
        } else if (btState.transportState == ConnectionState.TransportState.ACTIVE) {
            notificationBuilder.setContentText(btState.bclState.toStringResource(resources))
        } else {
            notificationBuilder.setContentText(btState.transportState.toStringResource(resources))
        }

        Logger.debug { "Showing MainNotification notification with btState $btState" }
        startForeground(ONGOING_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun subscribeConnections(btState: LiveData<ConnectionState>) {
        btState.observe(this) {
            startServiceNotification(it)
            scheduleShutdownTimeout()
        }
    }

    private fun stopServiceNotification() {
        stopForeground(true)
    }
    private fun shutdownBluetoothService() {
        unbindService(btServiceConnection)
        val intent = Intent(applicationContext, BtClientService::class.java).apply {
            action = BtClientService.ACTION_SHUTDOWN
        }
        startService(intent)
    }
    override fun onDestroy() {
        stopServiceNotification()
        shutdownBluetoothService()
        super.onDestroy()
    }
}