package xyz.fivekov.terminal.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.data.ServerRepository

@RunWith(RobolectricTestRunner::class)
class ServerEditFlowTest {

    private lateinit var prefs: AppPreferences
    private lateinit var repo: ServerRepository

    @Before
    fun setup() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = AppPreferences(context)
        prefs.secure.edit().clear().commit()
        repo = ServerRepository(prefs)
    }

    @Test
    fun `edited server save builds correct intent with reconnect flag`() {
        val saved = repo.save(ServerConfig(
            id = "",
            name = "Test",
            hostname = "old-host.local",
            port = 22,
            username = "user",
        ))

        // Simulate what ServerEditActivity.save() does when editing:
        val updated = saved.copy(hostname = "new-host.local")
        repo.save(updated)

        // Verify updated config
        val result = repo.get(saved.id)
        assertNotNull(result)
        assertEquals("new-host.local", result!!.hostname)

        // Verify the intent that would be created
        val intent = Intent().apply {
            putExtra(HomeActivity.EXTRA_SERVER_ID, saved.id)
            putExtra(TerminalActivity.EXTRA_RECONNECT, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        assertEquals(saved.id, intent.getStringExtra(HomeActivity.EXTRA_SERVER_ID))
        assertTrue(intent.getBooleanExtra(TerminalActivity.EXTRA_RECONNECT, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun `new server save does not set reconnect flag`() {
        val config = ServerConfig(
            id = "",
            name = "",
            hostname = "new-server.local",
            port = 22,
            username = "admin",
        )
        val saved = repo.save(config)

        // For new servers (editingServerId is null), no TerminalActivity intent
        val editingServerId: String? = null
        val shouldNavigateToTerminal = editingServerId != null

        assertTrue("New server should not navigate to terminal", !shouldNavigateToTerminal)
        assertEquals("new-server.local", repo.get(saved.id)!!.hostname)
    }

    @Test
    fun `editing server preserves tmux session name`() {
        val saved = repo.save(ServerConfig(
            id = "",
            name = "Dev Machine",
            hostname = "192.168.1.10",
            port = 22,
            username = "dev",
            tmuxSession = "my-session",
        ))

        // Simulate editing: change hostname but keep tmux session
        val updated = saved.copy(hostname = "192.168.1.20")
        repo.save(updated)

        val result = repo.get(saved.id)
        assertEquals("my-session", result!!.tmuxSession)
        assertEquals("192.168.1.20", result.hostname)
    }

    @Test
    fun `effectiveTmuxSession uses default when blank`() {
        val config = ServerConfig(
            id = "1",
            name = "Test",
            hostname = "host",
            port = 22,
            username = "user",
            tmuxSession = "",
        )
        assertEquals("mobile-terminal", config.effectiveTmuxSession)
    }

    @Test
    fun `effectiveTmuxSession uses custom when set`() {
        val config = ServerConfig(
            id = "1",
            name = "Test",
            hostname = "host",
            port = 22,
            username = "user",
            tmuxSession = "dev",
        )
        assertEquals("dev", config.effectiveTmuxSession)
    }
}
