package xyz.fivekov.terminal.ui

import android.webkit.JavascriptInterface
import android.webkit.WebView

class TerminalBridge(
    private val webView: WebView,
    private val onInput: (sessionId: String, data: String) -> Unit,
    private val onResize: (sessionId: String, cols: Int, rows: Int) -> Unit,
    private val onStartListening: () -> Unit,
    private val onStopListening: () -> Unit,
    private val onReconnect: (sessionId: String) -> Unit,
    private val onDestroySession: (sessionId: String) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenServerSettings: (serverId: String) -> Unit,
    private val onReady: () -> Unit,
    private val onThemeChanged: (String) -> Unit,
) {
    // --- JS → Kotlin ---

    @JavascriptInterface
    fun sendInput(sessionId: String, data: String) {
        onInput(sessionId, data)
    }

    @JavascriptInterface
    fun sendResize(sessionId: String, cols: Int, rows: Int) {
        onResize(sessionId, cols, rows)
    }

    @JavascriptInterface
    fun startListening() {
        onStartListening()
    }

    @JavascriptInterface
    fun stopListening() {
        onStopListening()
    }

    @JavascriptInterface
    fun requestReconnect(sessionId: String) {
        onReconnect(sessionId)
    }

    @JavascriptInterface
    fun destroySession(sessionId: String) {
        onDestroySession(sessionId)
    }

    @JavascriptInterface
    fun openSettings() {
        onOpenSettings()
    }

    @JavascriptInterface
    fun openServerSettings(serverId: String) {
        onOpenServerSettings(serverId)
    }

    @JavascriptInterface
    fun onWebViewReady() {
        onReady()
    }

    @JavascriptInterface
    fun onThemeChanged(theme: String) {
        onThemeChanged.invoke(theme)
    }

    // --- Kotlin → JS ---

    fun addTab(sessionId: String, name: String, serverId: String) {
        val escapedName = name.replace("'", "\\'")
        evalJs("window.NativeTerminal.addTab('$sessionId', '$escapedName', '$serverId')")
    }

    fun removeTab(sessionId: String) {
        evalJs("window.NativeTerminal.removeTab('$sessionId')")
    }

    fun setActiveTab(sessionId: String) {
        evalJs("window.NativeTerminal.setActiveTab('$sessionId')")
    }

    fun writeToTerminal(sessionId: String, data: String) {
        val escaped = data
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        evalJs("window.NativeTerminal.writeToTerminal('$sessionId', '$escaped')")
    }

    fun setConnectionStatus(sessionId: String, status: String, state: String) {
        val escapedStatus = status.replace("'", "\\'")
        evalJs("window.NativeTerminal.setConnectionStatus('$sessionId', '$escapedStatus', '$state')")
    }

    fun insertTranscript(text: String, isFinal: Boolean) {
        val escaped = text.replace("'", "\\'")
        evalJs("window.NativeTerminal.insertTranscript('$escaped', $isFinal)")
    }

    fun setTheme(theme: String) {
        evalJs("window.NativeTerminal.setTheme('$theme')")
    }

    private fun evalJs(js: String) {
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
