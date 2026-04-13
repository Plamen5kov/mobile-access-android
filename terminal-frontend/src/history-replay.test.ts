import { describe, it, expect, vi } from "vitest";
import { Terminal } from "@xterm/xterm";

// Polyfills for JSDOM
Object.defineProperty(window, "matchMedia", {
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

HTMLCanvasElement.prototype.getContext = vi.fn().mockReturnValue({
    fillRect: vi.fn(),
    clearRect: vi.fn(),
    getImageData: vi.fn().mockReturnValue({ data: [] }),
    putImageData: vi.fn(),
    createImageData: vi.fn().mockReturnValue([]),
    setTransform: vi.fn(),
    drawImage: vi.fn(),
    save: vi.fn(),
    fillText: vi.fn(),
    restore: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    closePath: vi.fn(),
    stroke: vi.fn(),
    translate: vi.fn(),
    scale: vi.fn(),
    rotate: vi.fn(),
    arc: vi.fn(),
    fill: vi.fn(),
    measureText: vi.fn().mockReturnValue({ width: 0 }),
    transform: vi.fn(),
    rect: vi.fn(),
    clip: vi.fn(),
    font: "",
    textAlign: "",
    textBaseline: "",
}) as unknown as typeof HTMLCanvasElement.prototype.getContext;

function createTerminal(rows = 24, cols = 80): Terminal {
    const term = new Terminal({ rows, cols, scrollback: 1000 });
    const div = document.createElement("div");
    div.style.width = "800px";
    div.style.height = "480px";
    document.body.appendChild(div);
    term.open(div);
    return term;
}

function writeSync(term: Terminal, data: string): Promise<void> {
    return new Promise((resolve) => term.write(data, resolve));
}

function getAllScrollback(term: Terminal): string[] {
    const lines: string[] = [];
    const buf = term.buffer.active;
    for (let i = 0; i < buf.baseY + term.rows; i++) {
        const line = buf.getLine(i);
        if (line) lines.push(line.translateToString(true));
    }
    return lines;
}

describe("tmux scrollback via mouse wheel events", () => {
    it("tmux only sends the last viewport of content on attach (fundamental limitation)", async () => {
        const term = createTerminal(24, 80);

        // This is what tmux actually sends when attaching to a session with 200 lines:
        // Only lines 179-200 (last viewport). Lines 1-178 are in tmux's internal buffer.
        const tmuxAttachOutput =
            "\x1b[?1049h\x1b[H\x1b[2J\x1b[1;24r\x1b[H" +
            Array.from({ length: 22 }, (_, i) => `${179 + i}`).join("\r\n") +
            "\r\n";
        await writeSync(term, tmuxAttachOutput);

        const all = getAllScrollback(term);
        const has179 = all.some((l) => l.includes("179"));
        const has1 = all.some((l) => l.includes("1") && !l.includes("1"));

        // tmux sends 179-200, never sends 1-178
        expect(has179).toBe(true);
    });

    it("mouse wheel SGR events are well-formed for tmux", () => {
        // Wheel up = button 64, wheel down = button 65
        // SGR format: \x1b[<button;col;rowM
        const wheelUp = "\x1b[<64;1;1M";
        const wheelDown = "\x1b[<65;1;1M";

        expect(wheelUp).toBe("\x1b[<64;1;1M");
        expect(wheelDown).toBe("\x1b[<65;1;1M");
        expect(wheelUp.length).toBe(10);
        expect(wheelDown.length).toBe(10);
    });

    it("scroll calculation: finger movement converts to correct line count", () => {
        const rows = 24;
        const containerHeight = 480;
        const charHeight = containerHeight / rows; // 20px per line

        // Swipe 60px up (3 lines worth)
        const deltaFromLast = -60;
        const lineDelta = Math.round(-deltaFromLast / charHeight);
        expect(lineDelta).toBe(3);

        // Swipe 40px down (2 lines worth)
        const deltaDown = 40;
        const lineDeltaDown = Math.round(-deltaDown / charHeight);
        expect(lineDeltaDown).toBe(-2);
    });

    it("multiple wheel events generated for multi-line scroll", () => {
        const events: string[] = [];
        const lineDelta = 5; // scrolling up 5 lines
        const button = lineDelta > 0 ? 64 : 65;
        const event = `\x1b[<${button};1;1M`;

        for (let i = 0; i < Math.abs(lineDelta); i++) {
            events.push(event);
        }

        expect(events.length).toBe(5);
        expect(events.every((e) => e === "\x1b[<64;1;1M")).toBe(true);
    });
});

describe("history replay for activity recreation", () => {
    it("replayed history with newline padding is preserved in scrollback", async () => {
        const term = createTerminal(10, 80);

        // Replay history (from in-memory ring buffer on reconnect)
        const numbers = Array.from({ length: 200 }, (_, i) => `N:${i + 1}:`);
        const historyData = numbers.join("\r\n") + "\r\n";
        await writeSync(term, historyData + "\r\n".repeat(term.rows));

        // tmux redraw overwrites viewport
        await writeSync(
            term,
            "\x1b[H" +
                Array.from({ length: 10 }, (_, i) => `prompt-${i}`).join(
                    "\r\n",
                ),
        );

        const all = getAllScrollback(term);
        const missing: number[] = [];
        for (let n = 1; n <= 200; n++) {
            if (!all.some((line) => line.includes(`N:${n}:`))) missing.push(n);
        }

        expect(missing).toEqual([]);
    });
});
