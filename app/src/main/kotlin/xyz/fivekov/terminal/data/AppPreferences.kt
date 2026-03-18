package xyz.fivekov.terminal.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    val secure: SharedPreferences =
        context.getSharedPreferences("terminal_secure_prefs", Context.MODE_PRIVATE)

    private val general: SharedPreferences =
        context.getSharedPreferences("terminal_prefs", Context.MODE_PRIVATE)

    var activeServerId: String?
        get() = general.getString(KEY_ACTIVE_SERVER, null)
        set(value) = general.edit().putString(KEY_ACTIVE_SERVER, value).apply()

    var theme: String
        get() = general.getString(KEY_THEME, "dark") ?: "dark"
        set(value) = general.edit().putString(KEY_THEME, value).apply()

    companion object {
        private const val KEY_ACTIVE_SERVER = "active_server_id"
        private const val KEY_THEME = "theme"
    }
}
