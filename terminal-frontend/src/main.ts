import "@xterm/xterm/css/xterm.css";
import "./styles.css";
import { SessionManager } from "./session-manager";
import { registerBridge, setBridgeScrollControl } from "./bridge";
import { setupInput, setupToolbar } from "./input";
import { applyTheme, setupViewportTracking, setupScrollToBottom } from "./ui";

applyTheme("dark");

const terminalContainer = document.getElementById("terminal-container")!;
const sessionManager = new SessionManager(terminalContainer);

const tabBar = document.getElementById("tab-bar")!;

setupInput(sessionManager);
setupToolbar(sessionManager);
registerBridge(sessionManager, tabBar);

// Wire scroll-to-bottom button to bridge
const scrollControl = setupScrollToBottom(sessionManager);
setBridgeScrollControl(scrollControl);

// Hide scroll-to-bottom when user scrolls back to the end
sessionManager.onScroll(() => {
    const term = sessionManager.getActiveTerminal();
    if (!term) return;
    if (term.buffer.active.viewportY >= term.buffer.active.baseY) {
        scrollControl.hide();
    } else {
        scrollControl.show();
    }
});

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

window.Android?.onWebViewReady();
