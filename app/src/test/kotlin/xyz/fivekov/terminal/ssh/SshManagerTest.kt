package xyz.fivekov.terminal.ssh

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import xyz.fivekov.terminal.data.ServerConfig

@OptIn(ExperimentalCoroutinesApi::class)
class SshManagerTest {

    private lateinit var keyManager: SshKeyManager
    private lateinit var tmuxHelper: TmuxHelper
    private lateinit var sshManager: SshManager

    @Before
    fun setup() {
        keyManager = mockk(relaxed = true)
        tmuxHelper = TmuxHelper()
        sshManager = SshManager(keyManager, tmuxHelper)
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        assertEquals(ConnectionState.DISCONNECTED, sshManager.state.value)
    }

    @Test
    fun `connect transitions to CONNECTING then errors without key`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        sshManager.attach(scope)

        every { keyManager.getKeyPair() } returns null

        sshManager.connect(
            ServerConfig("1", "test", "localhost", 22, "user")
        )
        advanceUntilIdle()

        // Should end up DISCONNECTED because no key was available
        assertEquals(ConnectionState.DISCONNECTED, sshManager.state.value)
    }

    @Test
    fun `sendInput does nothing when not connected`() {
        // Should not throw
        sshManager.sendInput("ls\r")
    }

    @Test
    fun `sendResize does nothing when not connected`() {
        // Should not throw
        sshManager.sendResize(120, 40)
    }

    @Test
    fun `disconnect from disconnected state is safe`() = runTest {
        sshManager.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, sshManager.state.value)
    }
}
