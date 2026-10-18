package com.mateof.tfmtv.cast.relay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mateof.tfmtv.MainActivity
import com.mateof.tfmtv.R
import com.mateof.tfmtv.cast.CastController
import com.mateof.tfmtv.cast.CastHints
import com.mateof.tfmtv.cast.CastLog
import com.mateof.tfmtv.cast.CastMonitor
import com.mateof.tfmtv.cast.CastPhase
import com.mateof.tfmtv.cast.CastSession
import com.mateof.tfmtv.cast.UpnpClient
import com.mateof.tfmtv.cast.tvStateLabel
import com.mateof.tfmtv.core.Format
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The notification of a send in progress, and what keeps it alive.
 *
 * In the foreground because nothing else survives the screen going off for the
 * length of a film, and through the relay this device is the one serving it.
 * Then it also holds a Wi-Fi lock — or the radio drops into power saving and
 * the picture stutters — and ends the relay if the local network goes away.
 * Following the television is the controller's job; this only shows it.
 */
@AndroidEntryPoint
class CastRelayService : Service() {

    @Inject lateinit var relay: CastRelay
    @Inject lateinit var monitor: CastMonitor
    @Inject lateinit var controller: CastController
    @Inject lateinit var log: CastLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var follower: Job? = null
    private var ticker: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var serving = -1

    @Volatile
    private var lastStartId = 0

    /** The end already told in a notice, so it is told once. */
    private var noticeFor: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Required promptly after startForegroundService, even to stop at once.
        enterForeground()

        when (intent?.action) {
            ACTION_STOP -> scope.launch {
                controller.stopCurrent()
                stopIfIdle(startId)
            }
            ACTION_TOGGLE -> controller.togglePause()
            ACTION_RECONNECT -> controller.reconnectInBackground()
        }

        if (!wanted() && intent?.action != ACTION_RECONNECT) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (follower?.isActive != true) {
            follower = scope.launch {
                relay.status.combine(monitor.session) { _, session -> session }.collect { session ->
                    adoptRelay()
                    if (session?.phase == CastPhase.ENDED) noticeEnd(session)
                    refreshNotification()
                    stopIfIdle(lastStartId)
                }
            }
        }
        // The position in the notification moves on by itself while it plays.
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive) {
                    delay(5_000)
                    refreshNotification()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseLocks()
        unwatchNetwork()
        scope.cancel()
        if (serving >= 0) relay.closeIfStill(serving, "servicio detenido")
        super.onDestroy()
    }

    private fun wanted(): Boolean {
        if (relay.status.value is RelayStatus.Running) return true
        val phase = monitor.session.value?.phase ?: return false
        return phase == CastPhase.ACTIVE || phase == CastPhase.RECONNECTING
    }

    /**
     * Stops unless there is still something to show. A new send sets its state
     * *before* starting this service again, and stopSelf(id) is ignored once a
     * newer start has arrived, so a send replacing another survives it.
     */
    private fun stopIfIdle(startId: Int) {
        if (!wanted()) stopSelf(startId)
    }

    private fun adoptRelay() {
        val running = relay.status.value as? RelayStatus.Running
        if (running == null) {
            releaseLocks()
            unwatchNetwork()
            return
        }
        serving = relay.generation
        holdLocks()
        watchNetwork()
    }

    private fun noticeEnd(session: CastSession) {
        val problem = session.endProblem ?: return
        if (noticeFor == session.endedAt) return
        noticeFor = session.endedAt
        postEndNotice(this, session.device.name, session.endHint ?: problem)
    }

    private fun watchNetwork() {
        if (networkCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                val current = relay.status.value as? RelayStatus.Running ?: return
                if (network == current.link.network) relay.end("se perdió la red local", CastHints.LAN_LOST)
            }
        }
        // Without the internet requirement the default request carries: the
        // television's network may well have no way out at all.
        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { log.w("No se pudo vigilar la red local", it.message) }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun holdLocks() {
        if (wifiLock == null) {
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tfmtv:relay")
                ?.apply { setReferenceCounted(false); acquire() }
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tfmtv:relay")
                ?.apply { setReferenceCounted(false); acquire() }
        }
    }

    private fun releaseLocks() {
        runCatching { wifiLock?.release() }
        runCatching { wakeLock?.release() }
        wifiLock = null
        wakeLock = null
    }

    private fun enterForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type) }
            .onFailure { log.w("No se pudo poner el envío en primer plano", it.message) }
    }

    private fun refreshNotification() {
        if (!wanted()) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, notification()) }
    }

    private fun notification(): Notification {
        ensureChannel(this)
        val running = relay.status.value as? RelayStatus.Running
        val session = monitor.session.value
        val device = session?.device?.name ?: running?.device?.name.orEmpty()
        val title = session?.request?.title ?: running?.title.orEmpty()
        val reconnecting = session?.phase == CastPhase.RECONNECTING

        val position = session?.let { s ->
            val elapsed = s.elapsedAt(System.currentTimeMillis()) ?: return@let null
            val duration = s.position?.durationMs
            UpnpClient.clock(elapsed) + (duration?.let { " / ${UpnpClient.clock(it)}" } ?: "")
        }
        val text = listOfNotNull(
            title,
            position,
            session?.takeIf { it.viaPhone && it.rateBytes > 0 }?.let { "${Format.bytes(it.rateBytes)}/s" }
        ).joinToString(" · ")
        val state = when {
            reconnecting -> "Reconectando…"
            session?.viaPhone == true || running != null -> "A través de este aparato · ${tvStateLabel(session?.tvState)}"
            else -> tvStateLabel(session?.tvState)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("En $device")
            .setContentText(text)
            .setSubText(state)
            .setSmallIcon(R.drawable.ic_cast_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setContentIntent(openPanel(this))
        if (session?.phase == CastPhase.ACTIVE) {
            builder.addAction(0, if (session.paused) "Reanudar" else "Pausa", service(ACTION_TOGGLE, 3))
        }
        return builder.addAction(0, "Parar", service(ACTION_STOP, 1)).build()
    }

    private fun service(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this,
        code,
        Intent(this, CastRelayService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val CHANNEL_ID = "tfmtv_cast"
        private const val NOTIFICATION_ID = 4812
        private const val NOTICE_ID = 4813
        private const val ACTION_STOP = "com.mateof.tfmtv.CAST_STOP"
        private const val ACTION_TOGGLE = "com.mateof.tfmtv.CAST_TOGGLE"
        private const val ACTION_RECONNECT = "com.mateof.tfmtv.CAST_RECONNECT"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, CastRelayService::class.java))
            }
        }

        /** Left behind after the send is gone, so a failure is not silent, with a way to try again. */
        fun postEndNotice(context: Context, deviceName: String, text: String) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(context)
            val intent = Intent(context, CastRelayService::class.java).setAction(ACTION_RECONNECT)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val reconnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 2, intent, flags)
            } else {
                PendingIntent.getService(context, 2, intent, flags)
            }
            val notice = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Se paró el envío a $deviceName")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_cast_notification)
                .setAutoCancel(true)
                .setContentIntent(openPanel(context))
                .addAction(0, "Reconectar", reconnect)
                .build()
            runCatching { manager.notify(NOTICE_ID, notice) }
        }

        fun clearEndNotice(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTICE_ID) }
        }

        private fun openPanel(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_CAST, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Envío a la tele", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }
}
