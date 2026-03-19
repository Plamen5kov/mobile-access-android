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

private data class SessionData(
    val sshManager: SshManager,
    val scope: CoroutineScope,
    val config: ServerConfig,
)

class SessionManager(
    private val keyManager: SshKeyManager,
    private val tmuxHelper: TmuxHelper,
) {
    private val sessions = ConcurrentHashMap<String, SessionData>()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId

    private val _sessionList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionList: StateFlow<List<SessionInfo>> = _sessionList

    fun createSession(config: ServerConfig, parentScope: CoroutineScope): String {
        val sessionId = UUID.randomUUID().toString()
        val sessionScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

        val sshManager = SshManager(keyManager, tmuxHelper)
        sshManager.attach(sessionScope)

        sessions[sessionId] = SessionData(sshManager, sessionScope, config)

        sessionScope.launch {
            sshManager.state.collect { updateSessionList() }
        }

        sessionScope.launch { sshManager.connect(config) }

        if (_activeSessionId.value == null) {
            _activeSessionId.value = sessionId
        }

        updateSessionList()
        return sessionId
    }

    fun destroySession(sessionId: String) {
        val data = sessions.remove(sessionId) ?: return

        data.scope.launch(Dispatchers.IO) {
            try {
                data.sshManager.disconnect()
            } catch (_: Exception) {}
            data.scope.cancel()
        }

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

    fun getSession(sessionId: String): SshManager? = sessions[sessionId]?.sshManager

    fun getActiveSession(): SshManager? {
        val id = _activeSessionId.value ?: return null
        return sessions[id]?.sshManager
    }

    fun sendInput(sessionId: String, data: String) {
        sessions[sessionId]?.sshManager?.sendInput(data)
    }

    fun sendResize(sessionId: String, cols: Int, rows: Int) {
        sessions[sessionId]?.sshManager?.sendResize(cols, rows)
    }

    suspend fun reconnectSession(sessionId: String) {
        sessions[sessionId]?.sshManager?.reconnect()
    }

    fun findSessionByServerId(serverId: String): String? {
        return sessions.entries.find { it.value.config.id == serverId }?.key
    }

    fun getAllSessionIds(): Set<String> = sessions.keys.toSet()

    fun destroyAll() {
        sessions.keys.toList().forEach { destroySession(it) }
    }

    private fun updateSessionList() {
        _sessionList.value = sessions.map { (id, data) ->
            SessionInfo(
                sessionId = id,
                serverId = data.config.id,
                serverDisplayName = data.config.displayName,
                state = data.sshManager.state.value,
            )
        }
    }
}
