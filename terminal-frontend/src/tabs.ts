import type { SessionManager } from "./session-manager";

export function renderTabs(
    sessionManager: SessionManager,
    tabBar: HTMLElement,
): void {
    const entries = sessionManager.getAllEntries();
    const activeId = sessionManager.getActiveSessionId();

    tabBar.textContent = "";

    for (const { sessionId, name } of entries) {
        const tab = document.createElement("div");
        tab.className = "tab" + (sessionId === activeId ? " active" : "");
        tab.dataset.sessionId = sessionId;

        const label = document.createElement("span");
        label.className = "tab-label";
        label.textContent = name;
        tab.appendChild(label);

        tab.addEventListener("click", () => {
            // Route through NativeTerminal so status bar gets restored
            window.NativeTerminal.setActiveTab(sessionId);
        });

        tabBar.appendChild(tab);
    }

    tabBar.style.display = entries.length > 0 ? "flex" : "none";

    // Show/hide the session close button
    const closeBtn = document.getElementById("session-close-btn");
    if (closeBtn) {
        closeBtn.classList.toggle("hidden", entries.length === 0);
    }
}
