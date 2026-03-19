import type { SessionManager } from "./session-manager";
import type { NativeTerminalApi, ThemeName } from "./types";
import { applyTheme } from "./ui";
import { renderTabs } from "./tabs";

const sessionStatus = new Map<string, { status: string; state: string }>();

function updateStatusBar(status: string, state: string) {
    const statusText = document.getElementById("status-text");
    const statusDot = document.getElementById("status-dot");
    const errorPanel = document.getElementById("error-panel");
    const errorMessage = document.getElementById("error-message");

    if (statusText) statusText.textContent = status;
    if (statusDot) {
        statusDot.className = "";
        if (state) statusDot.classList.add(state);
    }

    if (errorPanel && errorMessage) {
        if (state === "error") {
            errorMessage.textContent = status;
            errorPanel.classList.remove("hidden");
        } else if (state === "connected") {
            errorPanel.classList.add("hidden");
        }
    }
}

export function registerBridge(
    sessionManager: SessionManager,
    tabBar: HTMLElement,
): NativeTerminalApi {
    const api: NativeTerminalApi = {
        writeToTerminal(sessionId: string, data: string) {
            sessionManager.getTerminal(sessionId)?.write(data);
            const term = sessionManager.getTerminal(sessionId);
            if (term) requestAnimationFrame(() => term.scrollToBottom());
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
            // With direct terminal input, STT results go straight to the terminal
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
