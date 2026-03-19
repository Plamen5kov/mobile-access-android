package xyz.fivekov.terminal.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.TerminalApp
import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.ssh.SshKeyManager
import org.koin.android.ext.android.inject

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "SETTINGS"

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        syncAppIcon()
    }

    private fun syncAppIcon() {
        val prefs = org.koin.java.KoinJavaComponent.inject<AppPreferences>(AppPreferences::class.java).value
        val icon = prefs.appIcon
        val pm = packageManager
        val basePackage = packageName.removeSuffix(".debug")

        val aliases = mapOf(
            "green" to ".ui.LauncherGreen",
            "color" to ".ui.LauncherColor",
        )

        for ((key, alias) in aliases) {
            val component = ComponentName(packageName, "$basePackage$alias")
            val desired = if (key == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val current = pm.getComponentEnabledSetting(component)
            if (current != desired) {
                pm.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP)
            }
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val prefs: AppPreferences by inject()
        private val keyManager: SshKeyManager by inject()

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<ListPreference>("theme_mode")?.value = prefs.themeMode

            findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
                val mode = newValue as String
                prefs.themeMode = mode

                val icon = when (mode) {
                    "light" -> "color"
                    "system" -> {
                        val nightMode = resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) "green" else "color"
                    }
                    else -> "green"
                }
                prefs.appIcon = icon
                // Icon syncs on next cold start via TerminalApp.syncAppIcon()

                TerminalApp.applyThemeMode(mode)
                true
            }

            // SSH key
            findPreference<Preference>("ssh_key")?.apply {
                updateSshKeySummary(this)
                setOnPreferenceClickListener {
                    showSshKeyDialog()
                    true
                }
            }
        }

        private fun updateSshKeySummary(pref: Preference) {
            pref.summary = if (keyManager.hasKeyPair()) {
                val key = keyManager.getPublicKeyOpenSsh() ?: ""
                key.take(40) + "..."
            } else {
                getString(R.string.ssh_key_none)
            }
        }

        private fun showSshKeyDialog() {
            val activity = requireActivity()

            val builder = AlertDialog.Builder(activity)
                .setTitle(R.string.ssh_key_title)

            if (keyManager.hasKeyPair()) {
                val pubKey = keyManager.getPublicKeyOpenSsh() ?: ""
                builder.setMessage(pubKey)
                    .setPositiveButton(R.string.setup_copy_key) { _, _ ->
                        val clipboard = activity.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("SSH Public Key", pubKey))
                        Toast.makeText(activity, "Public key copied", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton(R.string.setup_generate_key) { _, _ ->
                        keyManager.generateKeyPair()
                        Toast.makeText(activity, "New SSH key generated", Toast.LENGTH_SHORT).show()
                        updateSshKeySummary(findPreference("ssh_key")!!)
                        showSshKeyDialog()
                    }
                    .setNegativeButton("Close", null)
            } else {
                builder.setMessage(R.string.ssh_key_none)
                    .setPositiveButton(R.string.setup_generate_key) { _, _ ->
                        keyManager.generateKeyPair()
                        Toast.makeText(activity, "SSH key generated", Toast.LENGTH_SHORT).show()
                        updateSshKeySummary(findPreference("ssh_key")!!)
                        showSshKeyDialog()
                    }
                    .setNegativeButton("Close", null)
            }

            builder.show()
        }
    }
}
