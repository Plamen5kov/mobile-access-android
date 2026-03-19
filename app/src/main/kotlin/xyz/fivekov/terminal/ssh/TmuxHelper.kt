package xyz.fivekov.terminal.ssh

class TmuxHelper {

    companion object {
        const val DEFAULT_SESSION = "mobile-terminal"
        private val VALID_SESSION_NAME = Regex("^[a-zA-Z0-9_.-]+$")
    }

    private fun sanitize(sessionName: String): String {
        require(sessionName.isNotBlank()) { "tmux session name must not be blank" }
        require(VALID_SESSION_NAME.matches(sessionName)) {
            "tmux session name contains invalid characters: $sessionName"
        }
        return sessionName
    }

    fun buildAttachCommand(sessionName: String = DEFAULT_SESSION): String {
        val name = sanitize(sessionName)
        return """
            if command -v tmux >/dev/null 2>&1; then
                tmux set-option -g aggressive-resize on 2>/dev/null
                tmux new-session -As $name
            else
                exec ${'$'}SHELL -l
            fi
        """.trimIndent()
    }

    fun buildDetachCommand(): String = "tmux detach-client"

    fun buildHasSessionCommand(sessionName: String = DEFAULT_SESSION): String {
        val name = sanitize(sessionName)
        return "tmux has-session -t $name 2>/dev/null"
    }
}
