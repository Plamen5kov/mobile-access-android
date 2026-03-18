package xyz.fivekov.terminal.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.fivekov.terminal.data.ServerConfig
import xyz.fivekov.terminal.data.SessionInfo
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
    private val keyManager: SshKeyManager,
    private val tmuxHelper: TmuxHelper,
) {
    private val sessions = ConcurrentHashMap<String, SshManager>()
    private val sessionScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val sessionConfigs = ConcurrentHashMap<String, ServerConfig>()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId

    private val _sessionList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionList: StateFlow<List<SessionInfo>> = _sessionList

    fun createSession(config: ServerConfig, parentScope: CoroutineScope): String {
        val sessionId = UUID.randomUUID().toString()

        // Each session gets its own SupervisorJob scope so failures are isolated
        val sessionScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

        val sshManager = SshManager(keyManager, tmuxHelper)
        sshManager.attach(sessionScope)

        sessions[sessionId] = sshManager
        sessionScopes[sessionId] = sessionScope
        sessionConfigs[sessionId] = config

        // Observe state changes to update session list
        sessionScope.launch {
            sshManager.state.collect { updateSessionList() }
        }

        // Connect
        sessionScope.launch { sshManager.connect(config) }

        // If this is the first session, make it active
        if (_activeSessionId.value == null) {
            _activeSessionId.value = sessionId
        }

        updateSessionList()
        return sessionId
    }

    fun destroySession(sessionId: String) {
        val sshManager = sessions.remove(sessionId) ?: return
        val scope = sessionScopes.remove(sessionId)
        sessionConfigs.remove(sessionId)

        // Cancel the session's scope (stops all coroutines including reconnect loops)
        scope?.cancel()

        // Disconnect in a best-effort way
        try {
            kotlinx.coroutines.runBlocking { sshManager.disconnect() }
        } catch (_: Exception) {}

        // If the active session was destroyed, switch to another
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = sessions.keys.firstOrNull()
        }

        updateSessionList()
    }

    fun switchTo(sessionId: String) {
        if (sessions.containsKey(sessionId)) {
            _activeSessionId.value = sessionId
        }
    }

    fun getSession(sessionId: String): SshManager? = sessions[sessionId]

    fun getActiveSession(): SshManager? {
        val id = _activeSessionId.value ?: return null
        return sessions[id]
    }

    fun sendInput(sessionId: String, data: String) {
        sessions[sessionId]?.sendInput(data)
    }

    fun sendResize(sessionId: String, cols: Int, rows: Int) {
        sessions[sessionId]?.sendResize(cols, rows)
    }

    suspend fun reconnectSession(sessionId: String) {
        sessions[sessionId]?.reconnect()
    }

    /** Find an existing session for a given server ID. */
    fun findSessionByServerId(serverId: String): String? {
        return sessionConfigs.entries.find { it.value.id == serverId }?.key
    }

    fun getAllSessionIds(): Set<String> = sessions.keys.toSet()

    fun destroyAll() {
        sessions.keys.toList().forEach { destroySession(it) }
    }

    private fun updateSessionList() {
        _sessionList.value = sessions.map { (id, manager) ->
            val config = sessionConfigs[id]
            SessionInfo(
                sessionId = id,
                serverId = config?.id ?: "",
                serverDisplayName = config?.displayName ?: "Unknown",
                state = manager.state.value,
            )
        }
    }
}
