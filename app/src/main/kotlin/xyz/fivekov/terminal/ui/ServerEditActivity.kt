package xyz.fivekov.terminal.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.data.ServerRepository
import xyz.fivekov.terminal.ssh.SshKeyManager
import org.koin.android.ext.android.inject

class ServerEditActivity : AppCompatActivity() {

    private val serverRepo: ServerRepository by inject()
    private val keyManager: SshKeyManager by inject()

    private lateinit var inputName: EditText
    private lateinit var inputHostname: EditText
    private lateinit var inputPort: EditText
    private lateinit var inputUsername: EditText
    private lateinit var inputTmuxSession: EditText

    private var editingServerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        inputName = findViewById(R.id.input_name)
        inputHostname = findViewById(R.id.input_hostname)
        inputPort = findViewById(R.id.input_port)
        inputUsername = findViewById(R.id.input_username)
        inputTmuxSession = findViewById(R.id.input_tmux_session)

        val titleView = findViewById<TextView>(R.id.setup_title)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnTest = findViewById<Button>(R.id.btn_test)
        val btnDelete = findViewById<Button>(R.id.btn_delete)
        val advancedToggle = findViewById<TextView>(R.id.btn_advanced_toggle)
        val advancedSection = findViewById<LinearLayout>(R.id.advanced_section)

        // Advanced section toggle
        advancedToggle.setOnClickListener {
            if (advancedSection.visibility == View.GONE) {
                advancedSection.visibility = View.VISIBLE
                advancedToggle.text = "[-] ADVANCED"
            } else {
                advancedSection.visibility = View.GONE
                advancedToggle.text = "[+] ADVANCED"
            }
        }

        editingServerId = intent.getStringExtra(HomeActivity.EXTRA_SERVER_ID)
        val existing = editingServerId?.let { serverRepo.get(it) }

        if (existing != null) {
            titleView.text = getString(R.string.server_edit_title_edit)
            inputName.setText(existing.name)
            inputHostname.setText(existing.hostname)
            inputPort.setText(existing.port.toString())
            inputUsername.setText(existing.username)
            if (existing.tmuxSession.isNotBlank()) {
                inputTmuxSession.setText(existing.tmuxSession)
                advancedSection.visibility = View.VISIBLE
                advancedToggle.text = "[-] ADVANCED"
            }
            btnDelete.visibility = View.VISIBLE
        } else {
            titleView.text = getString(R.string.server_edit_title_add)
            btnDelete.visibility = View.GONE
        }

        btnSave.setOnClickListener { save() }
        btnTest.setOnClickListener { testConnection() }
        btnDelete.setOnClickListener { delete() }
    }

    private fun save() {
        val hostname = inputHostname.text.toString().trim()
        val port = inputPort.text.toString().toIntOrNull() ?: 22
        val username = inputUsername.text.toString().trim()
        val name = inputName.text.toString().trim()
        val tmuxSession = inputTmuxSession.text.toString().trim()

        if (hostname.isBlank() || username.isBlank()) {
            Toast.makeText(this, "Hostname and username are required", Toast.LENGTH_SHORT).show()
            return
        }

        val config = ServerConfig(
            id = editingServerId ?: "",
            name = name.ifBlank { "$username@$hostname" },
            hostname = hostname,
            port = port,
            username = username,
            tmuxSession = tmuxSession,
        )
        val saved = serverRepo.save(config)

        // If we were editing an existing server, navigate to the terminal
        // (reconnects with updated config). Otherwise just go back.
        if (editingServerId != null) {
            val intent = Intent(this, TerminalActivity::class.java).apply {
                putExtra(HomeActivity.EXTRA_SERVER_ID, saved.id)
                putExtra(TerminalActivity.EXTRA_RECONNECT, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun testConnection() {
        val hostname = inputHostname.text.toString().trim()
        val port = inputPort.text.toString().toIntOrNull() ?: 22
        val username = inputUsername.text.toString().trim()

        if (hostname.isBlank() || username.isBlank()) {
            Toast.makeText(this, "Hostname and username are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!keyManager.hasKeyPair()) {
            Toast.makeText(this, "Generate SSH key first (from home screen)", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val conn = com.trilead.ssh2.Connection(hostname, port)
                    conn.connect(null, 5000, 5000)
                    val keyPair = keyManager.getKeyPair()!!
                    val ok = conn.authenticateWithPublicKey(username, keyPair)
                    conn.close()
                    ok
                }
                if (result) {
                    Toast.makeText(this@ServerEditActivity, "Connection successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@ServerEditActivity, "Authentication failed. Add the public key to authorized_keys.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ServerEditActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun delete() {
        val id = editingServerId ?: return
        serverRepo.delete(id)
        setResult(RESULT_OK)
        finish()
    }
}
