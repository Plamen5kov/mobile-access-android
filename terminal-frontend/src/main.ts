import "@xterm/xterm/css/xterm.css";
import "./styles.css";
import { SessionManager } from "./session-manager";
import { registerBridge } from "./bridge";
import { setupInput, setupToolbar } from "./input";
import { applyTheme, setupViewportTracking, setupTouchScrolling } from "./ui";

applyTheme("green");

const terminalContainer = document.getElementById("terminal-container")!;
const sessionManager = new SessionManager(terminalContainer);

const tabBar = document.getElementById("tab-bar")!;
const inputField = document.getElementById("dictation-text")!;
const { sendTextAndEnter } = setupInput(inputField, sessionManager);

setupToolbar(sessionManager);
registerBridge(sessionManager, tabBar, inputField, sendTextAndEnter);

// Theme toggle: green <-> amber
document.getElementById("theme-toggle")!.addEventListener("click", () => {
    const next = document.documentElement.classList.contains("amber")
        ? "green"
        : "amber";
    applyTheme(next);
    sessionManager.applyThemeToAll(next);
});

// Session close button (inside terminal area)
document.getElementById("session-close-btn")!.addEventListener("click", () => {
    const sid = sessionManager.getActiveSessionId();
    if (sid) window.Android?.destroySession(sid);
});

const app = document.getElementById("app")!;
setupViewportTracking(
    app,
    () => sessionManager.syncActiveSize(),
    sessionManager,
);
setupTouchScrolling(terminalContainer, sessionManager);

window.Android?.onWebViewReady();
