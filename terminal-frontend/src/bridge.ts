import type { SessionManager } from "./session-manager";
import type { NativeTerminalApi, ThemeName } from "./types";
import { applyTheme, isTerminalAtBottom } from "./ui";
import { renderTabs } from "./tabs";

const sessionStatus = new Map<string, { status: string; state: string }>();

const statusTextEl = document.getElementById("status-text");
const statusDotEl = document.getElementById("status-dot");
const errorPanelEl = document.getElementById("error-panel");
const errorMessageEl = document.getElementById("error-message");

let scrollBottomControl: { show: () => void; hide: () => void } | null = null;

export function setBridgeScrollControl(ctrl: {
    show: () => void;
    hide: () => void;
}) {
    scrollBottomControl = ctrl;
}

function updateStatusBar(status: string, state: string) {
    if (statusTextEl) statusTextEl.textContent = status;
    if (statusDotEl) {
        statusDotEl.className = "";
        if (state) statusDotEl.classList.add(state);
    }

    if (errorPanelEl && errorMessageEl) {
        if (state === "error") {
            errorMessageEl.textContent = status;
            errorPanelEl.classList.remove("hidden");
        } else if (state === "connected") {
            errorPanelEl.classList.add("hidden");
        }
    }
}

export function registerBridge(
    sessionManager: SessionManager,
    tabBar: HTMLElement,
): NativeTerminalApi {
    const api: NativeTerminalApi = {
        writeToTerminal(sessionId: string, data: string) {
            const term = sessionManager.getTerminal(sessionId);
            if (!term) return;

            const wasAtBottom = isTerminalAtBottom(sessionManager);
            term.write(data);

            if (wasAtBottom) {
                requestAnimationFrame(() => term.scrollToBottom());
                scrollBottomControl?.hide();
            } else if (sessionId === sessionManager.getActiveSessionId()) {
                scrollBottomControl?.show();
            }
        },

        setConnectionStatus(sessionId: string, status: string, state: string) {
            sessionStatus.set(sessionId, { status, state });
            if (sessionId === sessionManager.getActiveSessionId()) {
                updateStatusBar(status, state);
            }
        },

        addTab(sessionId: string, name: string, serverId: string) {
            sessionManager.addSession(sessionId, name, serverId);
            renderTabs(sessionManager, tabBar);
        },

        removeTab(sessionId: string) {
            sessionManager.removeSession(sessionId);
            sessionStatus.delete(sessionId);
            renderTabs(sessionManager, tabBar);
        },

        setActiveTab(sessionId: string) {
            sessionManager.switchTo(sessionId);
            renderTabs(sessionManager, tabBar);
            const cached = sessionStatus.get(sessionId);
            if (cached) {
                updateStatusBar(cached.status, cached.state);
            } else {
                updateStatusBar("CONNECTING", "connecting");
            }
        },

        insertTranscript(text: string, isFinal: boolean) {
            const sid = sessionManager.getActiveSessionId();
            if (sid && text) {
                window.Android?.sendInput(sid, text);
                if (isFinal) window.Android?.sendInput(sid, "\r");
            }
        },

        clearTerminal() {
            sessionManager.getActiveTerminal()?.clear();
        },

        setTheme(theme: ThemeName) {
            applyTheme(theme);
            sessionManager.applyThemeToAll(theme);
        },
    };

    window.NativeTerminal = api;
    return api;
}
