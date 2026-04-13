import type { ThemeName } from "./types";
import type { SessionManager } from "./session-manager";

let currentTheme: ThemeName = "dark";

export function getCurrentTheme(): ThemeName {
    return currentTheme;
}

export function applyTheme(theme: ThemeName) {
    currentTheme = theme;
    const root = document.documentElement;

    if (theme === "light") {
        root.classList.add("light");
    } else {
        root.classList.remove("light");
    }
}

export function setupViewportTracking(
    app: HTMLElement,
    syncSize: () => void,
    sessionManager: SessionManager,
) {
    function setHeight() {
        if (window.visualViewport) {
            app.style.height = `${window.visualViewport.height}px`;
        }
        window.scrollTo(0, 0);
        syncSize();
    }

    setHeight();

    if (window.visualViewport) {
        window.visualViewport.addEventListener("resize", () => {
            setHeight();
            if (isTerminalAtBottom(sessionManager)) {
                sessionManager.getActiveTerminal()?.scrollToBottom();
            }
        });
        window.visualViewport.addEventListener("scroll", setHeight);
    }

    // ResizeObserver catches container dimension changes that visualViewport misses
    const container = document.getElementById("terminal-container");
    if (container) {
        const ro = new ResizeObserver(() => syncSize());
        ro.observe(container);
    }
}

/** Check if the active terminal viewport is at (or near) the bottom of the scrollback. */
export function isTerminalAtBottom(sessionManager: SessionManager): boolean {
    const term = sessionManager.getActiveTerminal();
    if (!term) return true;
    const buf = term.buffer.active;
    return buf.viewportY >= buf.baseY;
}

/** Set up the "jump to bottom" button that appears when scrolled up. */
export function setupScrollToBottom(sessionManager: SessionManager): {
    show: () => void;
    hide: () => void;
} {
    const btn = document.getElementById("scroll-bottom-btn")!;

    btn.addEventListener("click", () => {
        sessionManager.getActiveTerminal()?.scrollToBottom();
        btn.classList.add("hidden");
    });

    return {
        show: () => btn.classList.remove("hidden"),
        hide: () => btn.classList.add("hidden"),
    };
}
