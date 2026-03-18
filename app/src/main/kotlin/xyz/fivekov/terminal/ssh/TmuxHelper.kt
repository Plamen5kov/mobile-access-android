package xyz.fivekov.terminal.ssh

class TmuxHelper {

    companion object {
        const val DEFAULT_SESSION = "mobile-terminal"
    }

    fun buildAttachCommand(sessionName: String = DEFAULT_SESSION): String {
        return """
            if command -v tmux >/dev/null 2>&1; then
                tmux new-session -As $sessionName
            else
                exec ${'$'}SHELL -l
            fi
        """.trimIndent()
    }

    fun buildDetachCommand(): String = "tmux detach-client"

    fun buildHasSessionCommand(sessionName: String = DEFAULT_SESSION): String {
        return "tmux has-session -t $sessionName 2>/dev/null"
    }
}
