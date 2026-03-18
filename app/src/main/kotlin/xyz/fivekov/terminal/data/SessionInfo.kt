package xyz.fivekov.terminal.data

import xyz.fivekov.terminal.ssh.ConnectionState

data class SessionInfo(
    val sessionId: String,
    val serverId: String,
    val serverDisplayName: String,
    val state: ConnectionState,
)
