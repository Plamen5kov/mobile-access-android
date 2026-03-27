import { describe, it, expect, beforeEach, vi } from "vitest";
import { isTerminalAtBottom, setupScrollToBottom } from "./ui";
import type { SessionManager } from "./session-manager";

function mockSessionManager(viewportY: number, baseY: number): SessionManager {
    return {
        getActiveTerminal: () =>
            ({
                buffer: { active: { viewportY, baseY } },
                scrollToBottom: vi.fn(),
            }) as any,
    } as any;
}

describe("isTerminalAtBottom", () => {
    it("returns true when viewport is at baseY", () => {
        expect(isTerminalAtBottom(mockSessionManager(100, 100))).toBe(true);
    });

    it("returns true when viewport exceeds baseY", () => {
        expect(isTerminalAtBottom(mockSessionManager(101, 100))).toBe(true);
    });

    it("returns false when viewport is above baseY", () => {
        expect(isTerminalAtBottom(mockSessionManager(50, 100))).toBe(false);
    });

    it("returns true when no active terminal", () => {
        const sm = {
            getActiveTerminal: () => undefined,
        } as any;
        expect(isTerminalAtBottom(sm)).toBe(true);
    });
});

describe("setupScrollToBottom", () => {
    let btn: HTMLButtonElement;

    beforeEach(() => {
        document.getElementById("scroll-bottom-btn")?.remove();
        btn = document.createElement("button");
        btn.id = "scroll-bottom-btn";
        btn.classList.add("hidden");
        document.body.appendChild(btn);
    });

    it("show removes hidden class", () => {
        const sm = mockSessionManager(0, 100);
        const ctrl = setupScrollToBottom(sm);
        ctrl.show();
        expect(btn.classList.contains("hidden")).toBe(false);
    });

    it("hide adds hidden class", () => {
        const sm = mockSessionManager(0, 100);
        const ctrl = setupScrollToBottom(sm);
        ctrl.show();
        ctrl.hide();
        expect(btn.classList.contains("hidden")).toBe(true);
    });

    it("clicking button scrolls to bottom and hides", () => {
        const terminal = {
            scrollToBottom: vi.fn(),
            buffer: { active: { viewportY: 0, baseY: 100 } },
        };
        const sm = {
            getActiveTerminal: () => terminal,
        } as any;
        const ctrl = setupScrollToBottom(sm);
        ctrl.show();
        btn.click();
        expect(terminal.scrollToBottom).toHaveBeenCalled();
        expect(btn.classList.contains("hidden")).toBe(true);
    });
});
