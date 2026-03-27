import { describe, it, expect, beforeEach, vi } from "vitest";
import { registerBridge, setBridgeScrollControl } from "./bridge";
import type { SessionManager } from "./session-manager";

function setupDOM() {
    const ids = [
        "status-text",
        "status-dot",
        "error-panel",
        "error-message",
        "tab-bar",
    ];
    for (const id of ids) {
        if (!document.getElementById(id)) {
            const el = document.createElement("div");
            el.id = id;
            document.body.appendChild(el);
        }
    }
    document.getElementById("error-panel")!.classList.add("hidden");
}

function createMockTerminal(viewportY: number, baseY: number) {
    return {
        buffer: { active: { viewportY, baseY } },
        write: vi.fn(),
        scrollToBottom: vi.fn(),
    };
}

function createMockSessionManager(
    terminal: ReturnType<typeof createMockTerminal> | undefined,
    activeId: string | null = "s1",
) {
    return {
        getActiveTerminal: () => terminal as any,
        getTerminal: (id: string) =>
            (id === activeId ? terminal : undefined) as any,
        getActiveSessionId: () => activeId,
        addSession: vi.fn(),
        removeSession: vi.fn(),
        switchTo: vi.fn(),
        getActiveServerId: () => null,
        getSessionCount: () => 1,
        getAllEntries: () => [],
        syncActiveSize: vi.fn(),
        applyThemeToAll: vi.fn(),
        onScroll: vi.fn(),
    } as unknown as SessionManager;
}

describe("writeToTerminal auto-scroll behavior", () => {
    let scrollControl: {
        show: ReturnType<typeof vi.fn>;
        hide: ReturnType<typeof vi.fn>;
    };

    beforeEach(() => {
        setupDOM();
        scrollControl = { show: vi.fn(), hide: vi.fn() };
        setBridgeScrollControl(scrollControl);
    });

    it("auto-scrolls and hides button when terminal is at bottom", async () => {
        const term = createMockTerminal(100, 100);
        const sm = createMockSessionManager(term);
        const tabBar = document.getElementById("tab-bar")!;
        const api = registerBridge(sm, tabBar);

        api.writeToTerminal("s1", "hello");

        expect(term.write).toHaveBeenCalledWith("hello");
        await new Promise((r) => requestAnimationFrame(r));
        expect(term.scrollToBottom).toHaveBeenCalled();
        expect(scrollControl.hide).toHaveBeenCalled();
        expect(scrollControl.show).not.toHaveBeenCalled();
    });

    it("shows button and does not auto-scroll when user is scrolled up", () => {
        const term = createMockTerminal(50, 100);
        const sm = createMockSessionManager(term);
        const tabBar = document.getElementById("tab-bar")!;
        const api = registerBridge(sm, tabBar);

        api.writeToTerminal("s1", "hello");

        expect(term.write).toHaveBeenCalledWith("hello");
        expect(term.scrollToBottom).not.toHaveBeenCalled();
        expect(scrollControl.show).toHaveBeenCalled();
    });

    it("does nothing for unknown session", () => {
        const term = createMockTerminal(100, 100);
        const sm = createMockSessionManager(term);
        const tabBar = document.getElementById("tab-bar")!;
        const api = registerBridge(sm, tabBar);

        api.writeToTerminal("unknown-session", "data");

        expect(term.write).not.toHaveBeenCalled();
        expect(scrollControl.show).not.toHaveBeenCalled();
        expect(scrollControl.hide).not.toHaveBeenCalled();
    });
});
