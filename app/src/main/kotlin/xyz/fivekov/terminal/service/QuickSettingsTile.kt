package xyz.fivekov.terminal.service

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService
import xyz.fivekov.terminal.ui.TerminalActivity

class QuickSettingsTile : TileService() {

    override fun onClick() {
        val intent = Intent(this, TerminalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }
}
