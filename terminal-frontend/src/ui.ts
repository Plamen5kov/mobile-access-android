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
            sessionManager.getActiveTerminal()?.scrollToBottom();
        });
        window.visualViewport.addEventListener("scroll", setHeight);
    }
}

export function setupTouchScrolling(
    container: HTMLElement,
    sessionManager: SessionManager,
) {
    let touchStartY = 0;

    container.addEventListener(
        "touchstart",
        (e) => {
            touchStartY = e.touches[0].clientY;
        },
        { passive: false },
    );

    container.addEventListener(
        "touchmove",
        (e) => {
            e.preventDefault();
            const terminal = sessionManager.getActiveTerminal();
            if (!terminal) return;
            const deltaY = touchStartY - e.touches[0].clientY;
            touchStartY = e.touches[0].clientY;
            if (deltaY > 0) terminal.scrollLines(2);
            else if (deltaY < 0) terminal.scrollLines(-2);
        },
        { passive: false },
    );
}
