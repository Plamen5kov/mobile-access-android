package xyz.fivekov.terminal.ssh

import org.junit.Assert.assertTrue
import org.junit.Test

class TmuxHelperTest {

    private val helper = TmuxHelper()

    @Test
    fun `attach command uses default session name`() {
        val cmd = helper.buildAttachCommand()
        assertTrue(cmd.contains(TmuxHelper.DEFAULT_SESSION))
    }

    @Test
    fun `attach command uses custom session name`() {
        val cmd = helper.buildAttachCommand("my-session")
        assertTrue(cmd.contains("my-session"))
    }

    @Test
    fun `attach command falls back to shell when tmux missing`() {
        val cmd = helper.buildAttachCommand()
        assertTrue(cmd.contains("exec \$SHELL"))
    }

    @Test
    fun `attach command uses tmux new-session with attach flag`() {
        val cmd = helper.buildAttachCommand()
        assertTrue(cmd.contains("tmux new-session -As"))
    }

    @Test
    fun `attach command sets history limit`() {
        val cmd = helper.buildAttachCommand()
        assertTrue(cmd.contains("history-limit"))
    }

    @Test
    fun `detach command is tmux detach-client`() {
        val cmd = helper.buildDetachCommand()
        assertTrue(cmd.contains("tmux detach-client"))
    }

    @Test
    fun `has session command uses default session`() {
        val cmd = helper.buildHasSessionCommand()
        assertTrue(cmd.contains(TmuxHelper.DEFAULT_SESSION))
        assertTrue(cmd.contains("has-session"))
    }

    @Test
    fun `has session command uses custom session`() {
        val cmd = helper.buildHasSessionCommand("dev")
        assertTrue(cmd.contains("dev"))
    }
}
