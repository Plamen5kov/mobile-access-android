package xyz.fivekov.terminal.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.data.ServerRepository
import xyz.fivekov.terminal.ssh.SshKeyManager
import org.koin.android.ext.android.inject

class HomeActivity : AppCompatActivity() {

    private val serverRepo: ServerRepository by inject()
    private val keyManager: SshKeyManager by inject()
    private val prefs: AppPreferences by inject()

    private lateinit var adapter: ServerAdapter
    private lateinit var emptyState: TextView

    companion object {
        const val EXTRA_SERVER_ID = "server_id"
        private const val REQUEST_ADD_EDIT = 1

        private val ICON_ALIASES = mapOf(
            "green" to ".ui.LauncherGreen",
            "color" to ".ui.LauncherColor",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        emptyState = findViewById(R.id.empty_state)

        adapter = ServerAdapter(
            onConnect = { server -> connectToServer(server) },
            onEdit = { server -> editServer(server) },
        )

        findViewById<RecyclerView>(R.id.server_list).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity)
            adapter = this@HomeActivity.adapter
        }

        findViewById<Button>(R.id.btn_add_server).setOnClickListener {
            addServer()
        }

        findViewById<ImageButton>(R.id.btn_ssh_key).setOnClickListener {
            showSshKeyDialog()
        }

        findViewById<TextView>(R.id.app_title).setOnClickListener {
            showIconPicker()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val servers = serverRepo.getAll()
        adapter.submitList(servers)
        emptyState.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun connectToServer(server: ServerConfig) {
        if (!keyManager.hasKeyPair()) {
            Toast.makeText(this, "Generate an SSH key first", Toast.LENGTH_SHORT).show()
            showSshKeyDialog()
            return
        }

        val intent = Intent(this, TerminalActivity::class.java).apply {
            putExtra(EXTRA_SERVER_ID, server.id)
        }
        startActivity(intent)
    }

    private fun addServer() {
        startActivityForResult(
            Intent(this, ServerEditActivity::class.java),
            REQUEST_ADD_EDIT
        )
    }

    private fun editServer(server: ServerConfig) {
        val intent = Intent(this, ServerEditActivity::class.java).apply {
            putExtra(EXTRA_SERVER_ID, server.id)
        }
        startActivityForResult(intent, REQUEST_ADD_EDIT)
    }

    private fun showIconPicker() {
        val current = prefs.appIcon
        val view = layoutInflater.inflate(R.layout.dialog_icon_picker, null)

        val previewGreen = view.findViewById<android.widget.ImageView>(R.id.preview_green)
        val previewColor = view.findViewById<android.widget.ImageView>(R.id.preview_color)
        val labelGreen = view.findViewById<TextView>(R.id.label_green)
        val labelColor = view.findViewById<TextView>(R.id.label_color)

        // Load preview images from assets
        try {
            assets.open("icon_preview_green.png").use {
                previewGreen.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it))
            }
            assets.open("icon_preview_color.png").use {
                previewColor.setImageBitmap(android.graphics.BitmapFactory.decodeStream(it))
            }
        } catch (_: Exception) {
            // Fallback to mipmap if assets missing
            previewGreen.setImageResource(R.mipmap.ic_launcher_green)
            previewColor.setImageResource(R.mipmap.ic_launcher_color)
        }

        // Highlight current selection
        if (current == "green") {
            labelGreen.text = "GREEN [*]"
            labelGreen.setTextColor(0xFF33FF33.toInt())
            labelColor.setTextColor(0xFF1A9A1A.toInt())
        } else {
            labelColor.text = "COLOR [*]"
            labelColor.setTextColor(0xFF33FF33.toInt())
            labelGreen.setTextColor(0xFF1A9A1A.toInt())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("APP ICON")
            .setView(view)
            .setNegativeButton("CANCEL", null)
            .create()

        view.findViewById<View>(R.id.pick_green).setOnClickListener {
            if (current != "green") setAppIcon("green")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.pick_color).setOnClickListener {
            if (current != "color") setAppIcon("color")
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setAppIcon(icon: String) {
        prefs.appIcon = icon

        val pm = packageManager
        val basePackage = packageName.removeSuffix(".debug")

        for ((key, alias) in ICON_ALIASES) {
            // Class name uses the base package, but ComponentName needs the actual package
            val component = ComponentName(packageName, "$basePackage$alias")
            val state = if (key == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }

        Toast.makeText(this, "Icon updated.", Toast.LENGTH_SHORT).show()
    }

    private fun showSshKeyDialog() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.ssh_key_title)

        if (keyManager.hasKeyPair()) {
            val pubKey = keyManager.getPublicKeyOpenSsh() ?: ""
            builder.setMessage(pubKey)
                .setPositiveButton(R.string.setup_copy_key) { _, _ ->
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("SSH Public Key", pubKey))
                    Toast.makeText(this, "Public key copied", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton(R.string.setup_generate_key) { _, _ ->
                    keyManager.generateKeyPair()
                    Toast.makeText(this, "New SSH key generated", Toast.LENGTH_SHORT).show()
                    showSshKeyDialog()
                }
                .setNegativeButton("Close", null)
        } else {
            builder.setMessage(R.string.ssh_key_none)
                .setPositiveButton(R.string.setup_generate_key) { _, _ ->
                    keyManager.generateKeyPair()
                    Toast.makeText(this, "SSH key generated", Toast.LENGTH_SHORT).show()
                    showSshKeyDialog()
                }
                .setNegativeButton("Close", null)
        }

        builder.show()
    }

    @Deprecated("Use ActivityResult API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_EDIT) {
            refreshList()
        }
    }
}
