import type { SessionManager } from "./session-manager";

let delegateInstalled = false;

export function renderTabs(
    sessionManager: SessionManager,
    tabBar: HTMLElement,
): void {
    if (!delegateInstalled) {
        tabBar.addEventListener("click", (e) => {
            const tab = (e.target as HTMLElement).closest<HTMLElement>(".tab");
            const sessionId = tab?.dataset.sessionId;
            if (sessionId) {
                window.NativeTerminal?.setActiveTab(sessionId);
            }
        });
        delegateInstalled = true;
    }

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

        tabBar.appendChild(tab);
    }

    tabBar.style.display = entries.length > 0 ? "flex" : "none";

    const closeBtn = document.getElementById("session-close-btn");
    if (closeBtn) {
        closeBtn.classList.toggle("hidden", entries.length === 0);
    }
}
