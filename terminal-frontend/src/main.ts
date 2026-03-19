import "@xterm/xterm/css/xterm.css";
import "./styles.css";
import { SessionManager } from "./session-manager";
import { registerBridge } from "./bridge";
import { setupInput, setupToolbar } from "./input";
import { applyTheme, setupViewportTracking, setupTouchScrolling } from "./ui";

applyTheme("dark");

const terminalContainer = document.getElementById("terminal-container")!;
const sessionManager = new SessionManager(terminalContainer);

const tabBar = document.getElementById("tab-bar")!;

setupInput(sessionManager);
setupToolbar(sessionManager);
registerBridge(sessionManager, tabBar);

// Session close button
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
