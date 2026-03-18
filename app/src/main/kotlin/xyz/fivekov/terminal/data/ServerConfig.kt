package xyz.fivekov.terminal.data

data class ServerConfig(
    val id: String,
    val name: String,
    val hostname: String,
    val port: Int = 22,
    val username: String,
    val tmuxSession: String = "",
) {
    val displayName: String
        get() = name.ifBlank { "$username@$hostname" }

    /** Returns the tmux session name, or the default if not set. */
    val effectiveTmuxSession: String
        get() = tmuxSession.ifBlank { "mobile-terminal" }
}
