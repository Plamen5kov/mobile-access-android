import type { SessionManager } from "./session-manager";

const LONG_PRESS_MS = 200;
const COPY_LONG_PRESS_MS = 500;

export function setupInput(sessionManager: SessionManager) {
    const actionBtn = document.getElementById("action-btn")!;

    // Tapping the terminal area focuses xterm.js for direct keyboard input
    document
        .getElementById("terminal-container")!
        .addEventListener("click", () => {
            sessionManager.getActiveTerminal()?.focus();
        });

    // Touch scroll + long-press to copy
    setupTerminalTouchScroll(sessionManager);

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

function setupTerminalTouchScroll(sessionManager: SessionManager) {
    const container = document.getElementById("terminal-container")!;
    let touchStartY = 0;
    let lastTouchY = 0;
    let isScrolling = false;
    let longPressTimer: ReturnType<typeof setTimeout> | null = null;
    let longPressFired = false;
    const SCROLL_THRESHOLD = 8;

    // Use capture phase to intercept before xterm.js's own touch handlers
    container.addEventListener(
        "touchstart",
        (e) => {
            if (e.touches.length !== 1) return;
            touchStartY = e.touches[0].clientY;
            lastTouchY = touchStartY;
            isScrolling = false;
            longPressFired = false;

            longPressTimer = setTimeout(() => {
                longPressTimer = null;
                if (!isScrolling) {
                    longPressFired = true;
                    handleLongPress(sessionManager, container, touchStartY);
                }
            }, COPY_LONG_PRESS_MS);
        },
        { capture: true, passive: true },
    );

    container.addEventListener(
        "touchmove",
        (e) => {
            if (e.touches.length !== 1) return;
            const currentY = e.touches[0].clientY;
            const deltaFromStart = currentY - touchStartY;
            const deltaFromLast = currentY - lastTouchY;

            // Cancel long press if finger moved
            if (Math.abs(deltaFromStart) > SCROLL_THRESHOLD) {
                if (longPressTimer) {
                    clearTimeout(longPressTimer);
                    longPressTimer = null;
                }
                isScrolling = true;
            }

            if (!isScrolling) {
                lastTouchY = currentY;
                return;
            }

            const term = sessionManager.getActiveTerminal();
            if (!term) return;

            // Send mouse wheel events to tmux so it handles scrollback
            const charHeight =
                container.getBoundingClientRect().height / term.rows;
            const lineDelta = Math.round(-deltaFromLast / charHeight);
            if (lineDelta !== 0) {
                const sid = sessionManager.getActiveSessionId();
                if (sid) {
                    const lines = Math.abs(lineDelta);
                    // SGR mouse wheel: \x1b[<64;col;rowM = wheel up, \x1b[<65;col;rowM = wheel down
                    const button = lineDelta > 0 ? 65 : 64;
                    const event = `\x1b[<${button};1;1M`;
                    for (let i = 0; i < lines; i++) {
                        window.Android?.sendInput(sid, event);
                    }
                }
                lastTouchY = currentY;
            }

            e.stopPropagation();
            e.preventDefault();
        },
        { capture: true, passive: false },
    );

    container.addEventListener(
        "touchend",
        () => {
            if (longPressTimer) {
                clearTimeout(longPressTimer);
                longPressTimer = null;
            }
            isScrolling = false;
        },
        { capture: true },
    );

    container.addEventListener(
        "touchcancel",
        () => {
            if (longPressTimer) {
                clearTimeout(longPressTimer);
                longPressTimer = null;
            }
            isScrolling = false;
        },
        { capture: true },
    );
}

function handleLongPress(
    sessionManager: SessionManager,
    container: HTMLElement,
    touchY: number,
) {
    const term = sessionManager.getActiveTerminal();
    if (!term) return;

    const rect = container.getBoundingClientRect();
    const y = touchY - rect.top;
    const charHeight = rect.height / term.rows;
    const row = Math.floor(y / charHeight);
    const bufferRow = term.buffer.active.viewportY + row;

    term.select(0, bufferRow, term.cols);

    const selection = term.getSelection();
    if (selection) {
        window.Android?.copyToClipboard(selection.trim());
        showCopyToast(container, rect.width / 2, y - 30);
    }

    setTimeout(() => term.clearSelection(), 1500);
}

function showCopyToast(parent: HTMLElement, x: number, y: number) {
    const toast = document.createElement("div");
    toast.textContent = "Copied";
    toast.style.cssText = `
        position: absolute;
        left: ${x}px;
        top: ${y - 30}px;
        background: var(--bg-secondary);
        color: var(--accent);
        border: 1px solid var(--accent);
        padding: 4px 10px;
        font: 11px var(--font);
        border-radius: 3px;
        pointer-events: none;
        z-index: 20;
        opacity: 1;
        transition: opacity 0.5s;
    `;
    parent.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = "0";
        setTimeout(() => toast.remove(), 500);
    }, 800);
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
        .getElementById("shift-tab-btn")!
        .addEventListener("click", () => send("\x1b[Z"));
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
