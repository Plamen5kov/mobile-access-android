package xyz.fivekov.terminal.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.TerminalApp
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.ssh.SessionManager
import xyz.fivekov.terminal.ui.TerminalActivity
import org.koin.android.ext.android.inject

class TerminalService : Service() {

    val sessionManager: SessionManager by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_disconnected)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

        serviceScope.launch {
            sessionManager.sessionList.collect { sessions ->
                val text = when {
                    sessions.isEmpty() -> getString(R.string.notification_disconnected)
                    sessions.size == 1 -> getString(
                        R.string.notification_connected,
                        sessions[0].serverDisplayName
                    )
                    else -> "${sessions.size} sessions active"
                }
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }

        return START_STICKY
    }

    fun createSession(server: ServerConfig): String {
        return sessionManager.createSession(server, serviceScope)
    }

    fun destroySession(sessionId: String) {
        sessionManager.destroySession(sessionId)
        if (sessionManager.getAllSessionIds().isEmpty()) {
            stopSelf()
        }
    }

    fun sendInput(sessionId: String, data: String) {
        sessionManager.sendInput(sessionId, data)
    }

    fun sendResize(sessionId: String, cols: Int, rows: Int) {
        sessionManager.sendResize(sessionId, cols, rows)
    }

    suspend fun reconnectSession(sessionId: String) {
        sessionManager.reconnectSession(sessionId)
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, TerminalApp.CHANNEL_TERMINAL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        sessionManager.destroyAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}
