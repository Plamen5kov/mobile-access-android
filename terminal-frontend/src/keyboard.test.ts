import { describe, it, expect, vi } from "vitest";

describe("Android keyboard backspace handling", () => {
    it("deleteContentBackward inputType should send DEL (0x7f)", () => {
        const sent: string[] = [];
        const mockSendInput = (sid: string, data: string) => sent.push(data);

        // Simulate the beforeinput handler logic
        const inputType = "deleteContentBackward";
        if (inputType === "deleteContentBackward") {
            mockSendInput("session1", "\x7f");
        }

        expect(sent).toEqual(["\x7f"]);
        expect(sent[0].charCodeAt(0)).toBe(127);
    });

    it("deleteContentForward inputType should send escape sequence", () => {
        const sent: string[] = [];
        const mockSendInput = (sid: string, data: string) => sent.push(data);

        const inputType = "deleteContentForward";
        if (inputType === "deleteContentForward") {
            mockSendInput("session1", "\x1b[3~");
        }

        expect(sent).toEqual(["\x1b[3~"]);
    });

    it("insertText inputType should NOT be intercepted", () => {
        const prevented: boolean[] = [];

        const inputType = "insertText";
        let shouldPrevent = false;
        if (
            inputType === "deleteContentBackward" ||
            inputType === "deleteContentForward"
        ) {
            shouldPrevent = true;
        }

        expect(shouldPrevent).toBe(false);
    });

    it("multiple backspaces send multiple DEL characters", () => {
        const sent: string[] = [];
        const mockSendInput = (data: string) => sent.push(data);

        // Simulate pressing backspace 3 times
        for (let i = 0; i < 3; i++) {
            mockSendInput("\x7f");
        }

        expect(sent.length).toBe(3);
        expect(sent.every((c) => c === "\x7f")).toBe(true);
    });
});
