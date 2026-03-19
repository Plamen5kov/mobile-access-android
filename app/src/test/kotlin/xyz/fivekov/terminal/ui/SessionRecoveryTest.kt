package xyz.fivekov.terminal.ui

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.ssh.ConnectionState
import xyz.fivekov.terminal.ssh.SessionManager
import xyz.fivekov.terminal.ssh.SshKeyManager
import xyz.fivekov.terminal.ssh.TmuxHelper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionRecoveryTest {

    private lateinit var sessionManager: SessionManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val brokenServer = ServerConfig(
        id = "srv-1",
        name = "Broken",
        hostname = "bad.local",
        port = 22,
        username = "user",
    )

    private val fixedServer = ServerConfig(
        id = "srv-1",
        name = "Fixed",
        hostname = "good.local",
        port = 22,
        username = "user",
    )

    @Before
    fun setup() {
        stopKoin()
        val keyManager: SshKeyManager = mockk(relaxed = true)
        every { keyManager.getKeyPair() } returns null
        sessionManager = SessionManager(keyManager, TmuxHelper())
    }

    @Test
    fun `reconnect flow - destroy old session and create new with updated config`() {
        // Create a session with broken config
        val oldSessionId = sessionManager.createSession(brokenServer, scope)
        assertNotNull(sessionManager.getSession(oldSessionId))
        assertEquals(oldSessionId, sessionManager.findSessionByServerId("srv-1"))

        // Simulate what TerminalActivity.onNewIntent does with EXTRA_RECONNECT:
        // 1. Find existing session for this server
        val existingId = sessionManager.findSessionByServerId("srv-1")
        assertEquals(oldSessionId, existingId)

        // 2. Destroy old session
        sessionManager.destroySession(existingId!!)
        assertNull(sessionManager.getSession(oldSessionId))

        // 3. Create new session with fixed config
        val newSessionId = sessionManager.createSession(fixedServer, scope)
        assertNotEquals(oldSessionId, newSessionId)
        assertNotNull(sessionManager.getSession(newSessionId))

        // 4. New session should be the active one
        assertEquals(newSessionId, sessionManager.activeSessionId.value)

        // 5. Session list should show updated server name
        val info = sessionManager.sessionList.value.find { it.sessionId == newSessionId }
        assertNotNull(info)
        assertEquals("Fixed", info!!.serverDisplayName)
    }

    @Test
    fun `reconnect preserves other sessions`() {
        val otherServer = ServerConfig(
            id = "srv-2",
            name = "Other",
            hostname = "other.local",
            port = 22,
            username = "user",
        )

        val brokenId = sessionManager.createSession(brokenServer, scope)
        val otherId = sessionManager.createSession(otherServer, scope)

        // Destroy broken, create fixed
        sessionManager.destroySession(brokenId)
        val fixedId = sessionManager.createSession(fixedServer, scope)

        // Other session still exists
        assertNotNull(sessionManager.getSession(otherId))
        // Fixed session exists
        assertNotNull(sessionManager.getSession(fixedId))
        // Two sessions total
        assertEquals(2, sessionManager.getAllSessionIds().size)
    }

    @Test
    fun `bridge callbacks route to correct server for error panel settings`() {
        var openedServerId: String? = null

        val bridge = TerminalBridge(
            js = { },
            onInput = { _, _ -> },
            onResize = { _, _, _ -> },
            onStartListening = {},
            onStopListening = {},
            onReconnect = { _ -> },
            onDestroySession = { _ -> },
            onOpenSettings = {},
            onOpenServerSettings = { serverId -> openedServerId = serverId },
            onReady = {},
        )

        // Simulate error panel settings click for a specific server
        bridge.openServerSettings("srv-1")
        assertEquals("srv-1", openedServerId)
    }

    @Test
    fun `bridge callbacks can destroy session during error state`() {
        var destroyedSessionId: String? = null

        val bridge = TerminalBridge(
            js = { },
            onInput = { _, _ -> },
            onResize = { _, _, _ -> },
            onStartListening = {},
            onStopListening = {},
            onReconnect = { _ -> },
            onDestroySession = { sid -> destroyedSessionId = sid },
            onOpenSettings = {},
            onOpenServerSettings = { _ -> },
            onReady = {},
        )

        // Create a broken session
        val sessionId = sessionManager.createSession(brokenServer, scope)

        // Simulate clicking close on error panel
        bridge.destroySession(sessionId)
        assertEquals(sessionId, destroyedSessionId)

        // Actually destroy it
        sessionManager.destroySession(sessionId)
        assertNull(sessionManager.getSession(sessionId))
    }
}
