package xyz.fivekov.terminal.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.data.ServerRepository
import xyz.fivekov.terminal.ssh.SshKeyManager
import org.koin.android.ext.android.inject

class HomeActivity : AppCompatActivity() {

    private val serverRepo: ServerRepository by inject()
    private val keyManager: SshKeyManager by inject()

    private lateinit var adapter: ServerAdapter
    private lateinit var emptyState: TextView

    companion object {
        const val EXTRA_SERVER_ID = "server_id"
    }

    private val editServerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshList() }

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
            startActivity(Intent(this, SettingsActivity::class.java))
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
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        val intent = Intent(this, TerminalActivity::class.java).apply {
            putExtra(EXTRA_SERVER_ID, server.id)
        }
        startActivity(intent)
    }

    private fun addServer() {
        editServerLauncher.launch(Intent(this, ServerEditActivity::class.java))
    }

    private fun editServer(server: ServerConfig) {
        val intent = Intent(this, ServerEditActivity::class.java).apply {
            putExtra(EXTRA_SERVER_ID, server.id)
        }
        editServerLauncher.launch(intent)
    }
}
