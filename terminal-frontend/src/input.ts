import type { SessionManager } from "./session-manager";

const LONG_PRESS_MS = 200;

export function setupInput(sessionManager: SessionManager) {
    const actionBtn = document.getElementById("action-btn")!;

    // Tapping the terminal area focuses xterm.js for direct keyboard input
    document
        .getElementById("terminal-container")!
        .addEventListener("click", () => {
            sessionManager.getActiveTerminal()?.focus();
        });

    // --- Action button: tap = send enter, long-press = push-to-talk ---
    let pressTimer: ReturnType<typeof setTimeout> | null = null;
    let isLongPress = false;
    let isRecording = false;

    function startRecording() {
        isRecording = true;
        actionBtn.textContent = "REC...";
        actionBtn.classList.add("recording");
        window.Android?.startListening();
    }

    function stopRecording() {
        isRecording = false;
        actionBtn.textContent = "mic/>";
        actionBtn.classList.remove("recording");
        window.Android?.stopListening();
    }

    function onPressStart(e: Event) {
        e.preventDefault();
        isLongPress = false;
        pressTimer = setTimeout(() => {
            isLongPress = true;
            startRecording();
        }, LONG_PRESS_MS);
    }

    function onPressEnd(e: Event) {
        e.preventDefault();
        if (pressTimer) {
            clearTimeout(pressTimer);
            pressTimer = null;
        }

        if (isRecording) {
            stopRecording();
        } else if (!isLongPress) {
            // Tap = send enter to active session
            const sid = sessionManager.getActiveSessionId();
            if (sid) window.Android?.sendInput(sid, "\r");
        }
        isLongPress = false;
    }

    function onPressCancel(e: Event) {
        e.preventDefault();
        if (pressTimer) {
            clearTimeout(pressTimer);
            pressTimer = null;
        }
        if (isRecording) stopRecording();
        isLongPress = false;
    }

    actionBtn.addEventListener("touchstart", onPressStart);
    actionBtn.addEventListener("touchend", onPressEnd);
    actionBtn.addEventListener("touchcancel", onPressCancel);
    actionBtn.addEventListener("mousedown", onPressStart);
    actionBtn.addEventListener("mouseup", onPressEnd);
}

export function setupToolbar(sessionManager: SessionManager) {
    const send = (data: string) => {
        const sid = sessionManager.getActiveSessionId();
        if (sid) window.Android?.sendInput(sid, data);
    };

    document
        .getElementById("esc-btn")!
        .addEventListener("click", () => send("\x1b"));
    document
        .getElementById("ctrlc-btn")!
        .addEventListener("click", () => send("\x03"));
    document
        .getElementById("tab-btn")!
        .addEventListener("click", () => send("\t"));
    document
        .getElementById("up-btn")!
        .addEventListener("click", () => send("\x1b[A"));
    document
        .getElementById("down-btn")!
        .addEventListener("click", () => send("\x1b[B"));

    document
        .getElementById("status-indicator")!
        .addEventListener("click", () => {
            const sid = sessionManager.getActiveSessionId();
            if (sid) window.Android?.requestReconnect(sid);
        });

    document.getElementById("settings-btn")!.addEventListener("click", () => {
        window.Android?.openSettings();
    });

    document.getElementById("reconnect-btn")!.addEventListener("click", () => {
        const sid = sessionManager.getActiveSessionId();
        if (sid) window.Android?.requestReconnect(sid);
    });

    document
        .getElementById("error-settings-btn")!
        .addEventListener("click", () => {
            const serverId = sessionManager.getActiveServerId();
            if (serverId) {
                window.Android?.openServerSettings(serverId);
            } else {
                window.Android?.openSettings();
            }
        });

    document
        .getElementById("error-close-btn")!
        .addEventListener("click", () => {
            const sid = sessionManager.getActiveSessionId();
            if (sid) window.Android?.destroySession(sid);
        });
}
