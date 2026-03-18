package xyz.fivekov.terminal.ssh

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.fivekov.terminal.data.ServerConfig

@OptIn(ExperimentalCoroutinesApi::class)
class BrokenSessionTest {

    private lateinit var sessionManager: SessionManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val server = ServerConfig(
        id = "srv-broken",
        name = "Broken Server",
        hostname = "unreachable.local",
        port = 22,
        username = "user",
        tmuxSession = "dev",
    )

    @Before
    fun setup() {
        val keyManager: SshKeyManager = mockk(relaxed = true)
        every { keyManager.getKeyPair() } returns null
        sessionManager = SessionManager(keyManager, TmuxHelper())
    }

    @Test
    fun `can destroy a session that failed to connect`() {
        val sessionId = sessionManager.createSession(server, scope)
        val session = sessionManager.getSession(sessionId)
        assertNotNull(session)

        // Session should be DISCONNECTED or CONNECTING (connect runs async and fails)
        val state = session!!.state.value
        assertTrue(
            "Expected DISCONNECTED or CONNECTING, got $state",
            state == ConnectionState.DISCONNECTED || state == ConnectionState.CONNECTING
        )

        // Should be able to destroy it regardless of state
        sessionManager.destroySession(sessionId)
        assertNull(sessionManager.getSession(sessionId))
        assertTrue(sessionManager.getAllSessionIds().isEmpty())
    }

    @Test
    fun `destroying broken session does not affect other sessions`() {
        val goodServer = server.copy(id = "srv-good", name = "Good Server")
        val brokenId = sessionManager.createSession(server, scope)
        val goodId = sessionManager.createSession(goodServer, scope)

        sessionManager.destroySession(brokenId)

        assertNull(sessionManager.getSession(brokenId))
        assertNotNull(sessionManager.getSession(goodId))
        assertEquals(1, sessionManager.getAllSessionIds().size)
    }

    @Test
    fun `destroying active broken session switches to next available`() {
        val otherServer = server.copy(id = "srv-other", name = "Other")
        val brokenId = sessionManager.createSession(server, scope)
        val otherId = sessionManager.createSession(otherServer, scope)

        // brokenId is active (created first)
        assertEquals(brokenId, sessionManager.activeSessionId.value)

        sessionManager.destroySession(brokenId)
        assertEquals(otherId, sessionManager.activeSessionId.value)
    }

    @Test
    fun `findSessionByServerId returns correct session for broken server`() {
        val sessionId = sessionManager.createSession(server, scope)

        val found = sessionManager.findSessionByServerId("srv-broken")
        assertEquals(sessionId, found)
    }

    @Test
    fun `findSessionByServerId returns null after session destroyed`() {
        val sessionId = sessionManager.createSession(server, scope)
        sessionManager.destroySession(sessionId)

        assertNull(sessionManager.findSessionByServerId("srv-broken"))
    }

    @Test
    fun `session list reflects correct state for broken session`() {
        sessionManager.createSession(server, scope)

        val list = sessionManager.sessionList.value
        assertEquals(1, list.size)
        assertEquals("Broken Server", list[0].serverDisplayName)
        assertEquals("srv-broken", list[0].serverId)
        assertTrue(
            "Expected DISCONNECTED or CONNECTING",
            list[0].state == ConnectionState.DISCONNECTED || list[0].state == ConnectionState.CONNECTING
        )
    }
}
