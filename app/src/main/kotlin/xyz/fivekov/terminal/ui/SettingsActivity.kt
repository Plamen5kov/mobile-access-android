package xyz.fivekov.terminal.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.speech.RecognitionService
import android.view.View
import android.widget.ImageView
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
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "SETTINGS"

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    fun syncAppIcon() {
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
                TerminalApp.applyThemeMode(mode)
                true
            }

            setupSpeechEnginePreference()

            findPreference<Preference>("app_icon")?.apply {
                summary = if (prefs.appIcon == "green") "Green Phosphor" else "Classic Color"
                setOnPreferenceClickListener {
                    showIconPickerDialog()
                    true
                }
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

        private fun setupSpeechEnginePreference() {
            val pref = findPreference<ListPreference>("speech_engine") ?: return
            val pm = requireContext().packageManager

            // Always available options
            val entries = mutableListOf("Built-in (Sherpa, offline)")
            val values = mutableListOf("builtin")

            // Discover installed RecognitionService providers
            val intent = Intent(RecognitionService.SERVICE_INTERFACE)
            val services: List<ResolveInfo> = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)

            for (service in services) {
                val si = service.serviceInfo
                val label = si.loadLabel(pm).toString()
                val component = "${si.packageName}/${si.name}"
                entries.add(label)
                values.add("service:$component")
            }

            // Keyboard voice input (always available as fallback)
            entries.add("Keyboard voice input")
            values.add("keyboard")

            pref.entries = entries.toTypedArray()
            pref.entryValues = values.toTypedArray()

            // If current value is "system" (old), migrate to first discovered service or builtin
            if (prefs.speechEngine == "system") {
                val firstService = values.firstOrNull { it.startsWith("service:") }
                prefs.speechEngine = firstService ?: "builtin"
            }

            pref.value = prefs.speechEngine
            pref.setOnPreferenceChangeListener { _, newValue ->
                prefs.speechEngine = newValue as String
                true
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

        private fun showIconPickerDialog() {
            val activity = requireActivity()
            val dialogView = layoutInflater.inflate(R.layout.dialog_icon_picker, null)

            // Set icon previews
            dialogView.findViewById<ImageView>(R.id.preview_green)
                .setImageResource(R.mipmap.ic_launcher_green)
            dialogView.findViewById<ImageView>(R.id.preview_color)
                .setImageResource(R.mipmap.ic_launcher_color)

            val dialog = AlertDialog.Builder(activity)
                .setTitle("App Icon")
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .create()

            fun selectIcon(icon: String) {
                prefs.appIcon = icon
                (activity as? SettingsActivity)?.syncAppIcon()
                findPreference<Preference>("app_icon")?.summary =
                    if (icon == "green") "Green Phosphor" else "Classic Color"
                dialog.dismiss()
            }

            dialogView.findViewById<View>(R.id.pick_green).setOnClickListener { selectIcon("green") }
            dialogView.findViewById<View>(R.id.pick_color).setOnClickListener { selectIcon("color") }

            dialog.show()
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
