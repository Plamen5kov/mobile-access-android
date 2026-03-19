package xyz.fivekov.terminal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.appcompat.app.AppCompatDelegate
import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.di.appModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TerminalApp : Application() {

    companion object {
        const val CHANNEL_TERMINAL = "terminal_connection"
        const val CHANNEL_ALERTS = "terminal_alerts"

        fun applyThemeMode(mode: String) {
            when (mode) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startKoin {
            androidContext(this@TerminalApp)
            modules(appModule)
        }

        val prefs: AppPreferences by inject()
        applyThemeMode(prefs.themeMode)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val connectionChannel = NotificationChannel(
            CHANNEL_TERMINAL,
            "Terminal Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing notification while connected to a remote terminal"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Terminal Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts from remote terminal (command completion, errors)"
        }

        manager.createNotificationChannel(connectionChannel)
        manager.createNotificationChannel(alertsChannel)
    }
}
