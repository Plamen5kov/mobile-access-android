package xyz.fivekov.terminal.ui

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalBridgeTest {

    private lateinit var webView: WebView
    private lateinit var bridge: TerminalBridge
    private var lastSessionId: String? = null
    private var lastInput: String? = null
    private var lastResizeSession: String? = null
    private var lastCols: Int = 0
    private var lastRows: Int = 0
    private var reconnectSessionId: String? = null
    private var destroyedSessionId: String? = null
    private var settingsOpened = false
    private var readyFired = false

    @Before
    fun setup() {
        stopKoin()
        webView = WebView(ApplicationProvider.getApplicationContext())

        bridge = TerminalBridge(
            webView = webView,
            onInput = { sid, data -> lastSessionId = sid; lastInput = data },
            onResize = { sid, cols, rows -> lastResizeSession = sid; lastCols = cols; lastRows = rows },
            onStartListening = {},
            onStopListening = {},
            onReconnect = { sid -> reconnectSessionId = sid },
            onDestroySession = { sid -> destroyedSessionId = sid },
            onOpenSettings = { settingsOpened = true },
            onOpenServerSettings = { },
            onReady = { readyFired = true },
        )
    }

    @Test
    fun `sendInput forwards sessionId and data`() {
        bridge.sendInput("session-1", "ls\r")
        assertEquals("session-1", lastSessionId)
        assertEquals("ls\r", lastInput)
    }

    @Test
    fun `sendResize forwards sessionId and dimensions`() {
        bridge.sendResize("session-2", 120, 40)
        assertEquals("session-2", lastResizeSession)
        assertEquals(120, lastCols)
        assertEquals(40, lastRows)
    }

    @Test
    fun `requestReconnect forwards sessionId`() {
        bridge.requestReconnect("session-3")
        assertEquals("session-3", reconnectSessionId)
    }

    @Test
    fun `destroySession forwards sessionId`() {
        bridge.destroySession("session-4")
        assertEquals("session-4", destroyedSessionId)
    }

    @Test
    fun `openSettings triggers callback`() {
        bridge.openSettings()
        assertTrue(settingsOpened)
    }

    @Test
    fun `onWebViewReady triggers callback`() {
        bridge.onWebViewReady()
        assertTrue(readyFired)
    }

}
