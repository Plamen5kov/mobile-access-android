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
class SessionManagerTest {

    private lateinit var keyManager: SshKeyManager
    private lateinit var tmuxHelper: TmuxHelper
    private lateinit var sessionManager: SessionManager

    private val testServer = ServerConfig(
        id = "srv-1",
        name = "Test Server",
        hostname = "localhost",
        port = 22,
        username = "user",
    )

    @Before
    fun setup() {
        keyManager = mockk(relaxed = true)
        every { keyManager.getKeyPair() } returns null
        tmuxHelper = TmuxHelper()
        sessionManager = SessionManager(keyManager, tmuxHelper)
    }

    @Test
    fun `initial state has no sessions`() {
        assertTrue(sessionManager.getAllSessionIds().isEmpty())
        assertNull(sessionManager.activeSessionId.value)
        assertTrue(sessionManager.sessionList.value.isEmpty())
    }

    @Test
    fun `createSession returns unique session ID`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id1 = sessionManager.createSession(testServer, scope)
        val id2 = sessionManager.createSession(testServer, scope)

        assertTrue(id1.isNotBlank())
        assertTrue(id2.isNotBlank())
        assertTrue(id1 != id2)
    }

    @Test
    fun `createSession sets first session as active`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id = sessionManager.createSession(testServer, scope)

        assertEquals(id, sessionManager.activeSessionId.value)
    }

    @Test
    fun `createSession adds to session list`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        sessionManager.createSession(testServer, scope)

        assertEquals(1, sessionManager.sessionList.value.size)
        assertEquals("Test Server", sessionManager.sessionList.value[0].serverDisplayName)
    }

    @Test
    fun `getSession returns SshManager for valid ID`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id = sessionManager.createSession(testServer, scope)

        assertNotNull(sessionManager.getSession(id))
    }

    @Test
    fun `getSession returns null for invalid ID`() {
        assertNull(sessionManager.getSession("nonexistent"))
    }

    @Test
    fun `switchTo changes active session`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id1 = sessionManager.createSession(testServer, scope)
        val id2 = sessionManager.createSession(testServer, scope)

        assertEquals(id1, sessionManager.activeSessionId.value)
        sessionManager.switchTo(id2)
        assertEquals(id2, sessionManager.activeSessionId.value)
    }

    @Test
    fun `destroySession removes session`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id = sessionManager.createSession(testServer, scope)

        sessionManager.destroySession(id)
        assertTrue(sessionManager.getAllSessionIds().isEmpty())
        assertNull(sessionManager.getSession(id))
    }

    @Test
    fun `destroySession switches active to next available`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val id1 = sessionManager.createSession(testServer, scope)
        val id2 = sessionManager.createSession(testServer, scope)

        sessionManager.switchTo(id1)
        sessionManager.destroySession(id1)

        assertEquals(id2, sessionManager.activeSessionId.value)
    }

    @Test
    fun `destroyAll clears everything`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        sessionManager.createSession(testServer, scope)
        sessionManager.createSession(testServer, scope)

        sessionManager.destroyAll()
        assertTrue(sessionManager.getAllSessionIds().isEmpty())
        assertNull(sessionManager.activeSessionId.value)
    }

    @Test
    fun `sendInput to nonexistent session does not throw`() {
        sessionManager.sendInput("nonexistent", "data")
    }

    @Test
    fun `sendResize to nonexistent session does not throw`() {
        sessionManager.sendResize("nonexistent", 80, 24)
    }
}
