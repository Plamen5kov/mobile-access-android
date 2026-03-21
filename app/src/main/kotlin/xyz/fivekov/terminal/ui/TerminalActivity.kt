package xyz.fivekov.terminal.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import xyz.fivekov.terminal.R
import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.data.ServerRepository
import xyz.fivekov.terminal.service.TerminalService
import xyz.fivekov.terminal.ssh.ConnectionState
import org.koin.android.ext.android.inject

class TerminalActivity : AppCompatActivity() {

    private val prefs: AppPreferences by inject()
    private val serverRepo: ServerRepository by inject()

    private lateinit var webView: WebView
    private var bridge: TerminalBridge? = null
    private var terminalService: TerminalService? = null
    private var serviceBound = false

    private val observationJobs = mutableMapOf<String, List<Job>>()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startSpeechRecognition()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as TerminalService.LocalBinder).getService()
            terminalService = service
            serviceBound = true
            onServiceReady(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalService = null
            serviceBound = false
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    companion object {
        const val EXTRA_RECONNECT = "reconnect"
    }

    private var pendingServerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pendingServerId = intent.getStringExtra(HomeActivity.EXTRA_SERVER_ID)
        setupWebView()
        setupKeyboardInsets()
        registerNetworkCallback()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val serverId = intent.getStringExtra(HomeActivity.EXTRA_SERVER_ID) ?: return
        val reconnect = intent.getBooleanExtra(EXTRA_RECONNECT, false)
        val service = terminalService ?: return

        val existingSessionId = service.sessionManager.findSessionByServerId(serverId)
        if (existingSessionId != null && !reconnect) {
            // Just switch to existing tab
            bridge?.setActiveTab(existingSessionId)
            return
        }

        // Destroy old session if reconnecting with updated config
        if (existingSessionId != null) {
            service.destroySession(existingSessionId)
            bridge?.removeTab(existingSessionId)
            observationJobs[existingSessionId]?.forEach { it.cancel() }
            observationJobs.remove(existingSessionId)
        }

        val server = serverRepo.get(serverId) ?: return
        val sessionId = service.createSession(server)
        observeSession(service, sessionId)
        bridge?.addTab(sessionId, server.displayName, server.id)
        bridge?.setActiveTab(sessionId)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.terminal_webview)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        bridge = TerminalBridge(
            js = WebViewJsEvaluator(webView),
            onInput = { sessionId, data -> terminalService?.sendInput(sessionId, data) },
            onResize = { sessionId, cols, rows -> terminalService?.sendResize(sessionId, cols, rows) },
            onStartListening = { requestSpeechRecognition() },
            onStopListening = { stopSpeechRecognition() },
            onReconnect = { sessionId ->
                lifecycleScope.launch {
                    terminalService?.reconnectSession(sessionId)
                }
            },
            onDestroySession = { sessionId ->
                terminalService?.destroySession(sessionId)
                bridge?.removeTab(sessionId)
                observationJobs[sessionId]?.forEach { it.cancel() }
                observationJobs.remove(sessionId)
                // If no sessions left, go back to home
                if (terminalService?.sessionManager?.getAllSessionIds()?.isEmpty() == true) {
                    finish()
                }
            },
            onOpenSettings = {
                startActivity(Intent(this@TerminalActivity, HomeActivity::class.java))
            },
            onOpenServerSettings = { serverId ->
                startActivity(Intent(this@TerminalActivity, ServerEditActivity::class.java).apply {
                    putExtra(HomeActivity.EXTRA_SERVER_ID, serverId)
                })
            },
            onReady = { onWebViewReady() },
        )
        webView.addJavascriptInterface(bridge!!, "Android")
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    private fun onWebViewReady() {
        val intent = Intent(this, TerminalService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Pass the resolved theme (dark/light) to the WebView
        val resolvedTheme = when (prefs.themeMode) {
            "light" -> "light"
            "system" -> {
                val nightMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
            }
            else -> "dark"
        }
        bridge?.setTheme(resolvedTheme)
    }

    private fun onServiceReady(service: TerminalService) {
        // Reconnect to any existing sessions (e.g., after activity recreation)
        val existingSessions = service.sessionManager.getAllSessionIds()
        for (sessionId in existingSessions) {
            val session = service.sessionManager.getSession(sessionId) ?: continue
            val info = service.sessionManager.sessionList.value.find { it.sessionId == sessionId }
            bridge?.addTab(sessionId, info?.serverDisplayName ?: "Session", info?.serverId ?: "")
            observeSession(service, sessionId)
        }

        val activeId = service.sessionManager.activeSessionId.value
        if (activeId != null) {
            bridge?.setActiveTab(activeId)
        }

        // Create or switch to session for the requested server
        val serverId = pendingServerId
        pendingServerId = null
        if (serverId != null) {
            val existingSessionId = service.sessionManager.findSessionByServerId(serverId)
            if (existingSessionId != null) {
                bridge?.setActiveTab(existingSessionId)
            } else {
                val server = serverRepo.get(serverId)
                if (server != null) {
                    val sessionId = service.createSession(server)
                    observeSession(service, sessionId)
                    bridge?.addTab(sessionId, server.displayName, server.id)
                    bridge?.setActiveTab(sessionId)
                }
            }
        }
    }

    private fun observeSession(service: TerminalService, sessionId: String) {
        val sshManager = service.sessionManager.getSession(sessionId) ?: return

        val stateJob = lifecycleScope.launch {
            sshManager.state.collect { state ->
                val (statusText, cssClass) = when (state) {
                    ConnectionState.CONNECTED -> "Connected" to "connected"
                    ConnectionState.CONNECTING -> "Connecting..." to "connecting"
                    ConnectionState.RECONNECTING -> "Reconnecting..." to "connecting"
                    ConnectionState.DISCONNECTED -> "Disconnected" to "error"
                }
                bridge?.setConnectionStatus(sessionId, statusText, cssClass)
            }
        }

        val outputJob = lifecycleScope.launch {
            sshManager.output.collect { data ->
                bridge?.writeToTerminal(sessionId, data)
            }
        }

        val errorJob = lifecycleScope.launch {
            sshManager.error.collect { error ->
                bridge?.setConnectionStatus(sessionId, "Error: $error", "error")
            }
        }

        observationJobs[sessionId] = listOf(stateJob, outputJob, errorJob)
    }

    private fun requestSpeechRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("TerminalSTT", "Speech recognition not available on this device")
            bridge?.insertTranscript("[Speech recognition unavailable]", true)
            return
        }

        isListening = true
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onPartialResults(partialResults: Bundle?) {
                // Partials are cumulative (full text so far), not incremental.
                // Skip sending to terminal to avoid duplicate text.
            }

            override fun onResults(results: Bundle?) {
                val texts = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = texts?.firstOrNull() ?: return
                bridge?.insertTranscript(text, true)
                isListening = false
            }

            override fun onError(error: Int) {
                Log.w("TerminalSTT", "Speech recognition error: $error")
                isListening = false
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    private fun stopSpeechRecognition() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }

    private fun setupKeyboardInsets() {
        val container = findViewById<android.view.View>(R.id.webview_container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                systemBarInsets.left,
                systemBarInsets.top,
                systemBarInsets.right,
                maxOf(imeInsets.bottom, systemBarInsets.bottom),
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lifecycleScope.launch {
                    val sm = terminalService?.sessionManager ?: return@launch
                    for (sessionId in sm.getAllSessionIds()) {
                        val session = sm.getSession(sessionId) ?: continue
                        if (session.state.value == ConnectionState.DISCONNECTED) {
                            sm.reconnectSession(sessionId)
                        }
                    }
                }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    override fun onDestroy() {
        stopSpeechRecognition()
        networkCallback?.let {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
        }
        observationJobs.values.flatten().forEach { it.cancel() }
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
