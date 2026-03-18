import type { SessionManager } from "./session-manager";

const LONG_PRESS_MS = 200;

export function setupInput(
    inputField: HTMLElement,
    sessionManager: SessionManager,
) {
    const actionBtn = document.getElementById("action-btn")!;

    function getText(): string {
        return (inputField.innerText || inputField.textContent || "").trim();
    }

    function clear() {
        inputField.textContent = "";
    }

    function sendTextAndEnter() {
        const text = getText();
        const data = text ? text + "\r" : "\r";
        const sid = sessionManager.getActiveSessionId();
        if (sid) window.Android?.sendInput(sid, data);
        clear();
        focusTerminal();
    }

    function focusTerminal() {
        const term = sessionManager.getActiveTerminal();
        if (term) term.focus();
        inputField.classList.remove("active");
    }

    // Tapping the terminal area focuses xterm.js
    document
        .getElementById("terminal-container")!
        .addEventListener("click", () => {
            focusTerminal();
        });

    inputField.addEventListener("focus", () => {
        inputField.classList.add("active");
    });

    inputField.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendTextAndEnter();
        }
    });

    inputField.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = e.clipboardData?.getData("text/plain") ?? "";
        document.execCommand("insertText", false, text);
    });

    // --- Action button: tap = send, long-press = push-to-talk ---
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
            sendTextAndEnter();
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

    // Floating keys
    const floatKeyMap: Record<string, string> = {
        tab: "\t",
        up: "\x1b[A",
        down: "\x1b[B",
        enter: "\r",
    };

    document
        .querySelectorAll<HTMLButtonElement>(".float-key")
        .forEach((btn) => {
            const action = btn.dataset.action ?? "";
            const key = floatKeyMap[action];
            if (!key) return;

            btn.addEventListener("touchstart", (e) => {
                e.preventDefault();
                const sid = sessionManager.getActiveSessionId();
                if (sid) window.Android?.sendInput(sid, key);
            });
            btn.addEventListener("mousedown", (e) => {
                e.preventDefault();
                const sid = sessionManager.getActiveSessionId();
                if (sid) window.Android?.sendInput(sid, key);
            });
        });

    return { sendTextAndEnter };
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
}
