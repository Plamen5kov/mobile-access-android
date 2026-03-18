package xyz.fivekov.terminal.ssh

import com.trilead.ssh2.Connection
import com.trilead.ssh2.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.fivekov.terminal.data.ServerConfig

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}

class SshManager(
    private val keyManager: SshKeyManager,
    private val tmuxHelper: TmuxHelper,
) {
    private var connection: Connection? = null
    private var session: Session? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val output: SharedFlow<String> = _output

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val error: SharedFlow<String> = _error

    private var currentConfig: ServerConfig? = null
    private var scope: CoroutineScope? = null

    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    suspend fun connect(config: ServerConfig) {
        currentConfig = config
        _state.value = ConnectionState.CONNECTING

        try {
            withContext(Dispatchers.IO) {
                val conn = Connection(config.hostname, config.port)
                conn.connect(null, 10000, 10000)

                val keyPair = keyManager.getKeyPair()
                    ?: throw IllegalStateException("No SSH key pair generated")

                val authenticated = conn.authenticateWithPublicKey(
                    config.username,
                    keyPair
                )

                if (!authenticated) {
                    conn.close()
                    throw SecurityException("SSH authentication failed")
                }

                connection = conn

                val sess = conn.openSession()
                sess.requestPTY("xterm-256color", 80, 24, 0, 0, null)

                val command = tmuxHelper.buildAttachCommand(config.effectiveTmuxSession)
                sess.execCommand(command)

                session = sess
            }

            _state.value = ConnectionState.CONNECTED
            startReading()
            startKeepAlive()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = ConnectionState.DISCONNECTED
            _error.emit(e.message ?: "Connection failed")
        }
    }

    fun sendInput(data: String) {
        val sess = session ?: return
        scope?.launch(Dispatchers.IO) {
            try {
                sess.stdin.write(data.toByteArray())
                sess.stdin.flush()
            } catch (e: Exception) {
                _error.emit("Write failed: ${e.message}")
                handleDisconnect()
            }
        }
    }

    fun sendResize(cols: Int, rows: Int) {
        session?.resizePTY(cols, rows, 0, 0)
    }

    /** Gracefully disconnect: detach from tmux (keeps session alive), then close SSH. */
    suspend fun disconnect() {
        readJob?.cancel()
        keepAliveJob?.cancel()
        withContext(Dispatchers.IO) {
            // Send tmux detach so the server-side session stays alive
            try {
                val sess = session
                if (sess != null) {
                    sess.stdin.write(tmuxHelper.buildDetachCommand().toByteArray())
                    sess.stdin.write("\n".toByteArray())
                    sess.stdin.flush()
                    Thread.sleep(100) // Brief wait for detach to process
                }
            } catch (_: Exception) {
                // Best effort; session may already be closed
            }
            session?.close()
            connection?.close()
        }
        session = null
        connection = null
        _state.value = ConnectionState.DISCONNECTED
    }

    suspend fun reconnect() {
        val config = currentConfig ?: return
        _state.value = ConnectionState.RECONNECTING
        disconnect()

        var attempt = 0
        val maxDelay = 30_000L
        while (true) {
            attempt++
            try {
                connect(config)
                if (_state.value == ConnectionState.CONNECTED) return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // will retry
            }

            // Exponential backoff: 2s, 4s, 8s, 16s, 30s max
            val delayMs = minOf(2000L * (1 shl minOf(attempt - 1, 4)), maxDelay)
            _state.value = ConnectionState.DISCONNECTED

            // Emit countdown so UI can show remaining time
            val steps = (delayMs / 1000).toInt()
            for (s in steps downTo 1) {
                _error.emit("Reconnecting in ${s}s...")
                delay(1000)
            }

            _state.value = ConnectionState.RECONNECTING
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope?.launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            val input = session?.stdout ?: return@launch
            try {
                while (isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    val text = String(buffer, 0, bytesRead)
                    _output.emit(text)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // stream closed
            }
            if (isActive) {
                handleDisconnect()
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope?.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000)
                try {
                    connection?.sendIgnorePacket()
                } catch (_: Exception) {
                    handleDisconnect()
                    break
                }
            }
        }
    }

    private fun handleDisconnect() {
        if (_state.value == ConnectionState.RECONNECTING) return
        _state.value = ConnectionState.DISCONNECTED
        scope?.launch {
            _error.emit("Connection lost")
            reconnect()
        }
    }
}
