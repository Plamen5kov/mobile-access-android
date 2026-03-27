import { Terminal, type IDisposable } from "@xterm/xterm";
import { FitAddon } from "@xterm/addon-fit";
import { WebglAddon } from "@xterm/addon-webgl";
import { CanvasAddon } from "@xterm/addon-canvas";
import { WebLinksAddon } from "@xterm/addon-web-links";
import { Unicode11Addon } from "@xterm/addon-unicode11";
import { getTheme } from "./themes";
import { getCurrentTheme } from "./ui";

interface SessionEntry {
    sessionId: string;
    name: string;
    serverId: string;
    terminal: Terminal;
    fitAddon: FitAddon;
    container: HTMLDivElement;
    disposables: IDisposable[];
}

export class SessionManager {
    private sessions = new Map<string, SessionEntry>();
    private activeSessionId: string | null = null;
    private parentContainer: HTMLElement;
    private scrollListeners: Array<() => void> = [];

    constructor(parentContainer: HTMLElement) {
        this.parentContainer = parentContainer;
    }

    /** Register a callback that fires whenever any terminal scrolls. */
    onScroll(listener: () => void): void {
        this.scrollListeners.push(listener);
    }

    private notifyScroll(): void {
        for (const fn of this.scrollListeners) fn();
    }

    addSession(
        sessionId: string,
        name: string,
        serverId: string = "",
    ): Terminal {
        if (this.sessions.has(sessionId)) {
            return this.sessions.get(sessionId)!.terminal;
        }

        const container = document.createElement("div");
        container.className = "session-container";
        container.dataset.sessionId = sessionId;
        container.style.display = "none";
        this.parentContainer.appendChild(container);

        const terminal = new Terminal({
            cursorBlink: true,
            fontSize: 14,
            scrollback: 5000,
            fontFamily: 'Menlo, Monaco, "Courier New", monospace',
            theme: getTheme(getCurrentTheme()),
            allowProposedApi: true,
        });

        const fitAddon = new FitAddon();
        terminal.loadAddon(fitAddon);

        const unicode11 = new Unicode11Addon();
        terminal.loadAddon(unicode11);
        terminal.unicode.activeVersion = "11";

        terminal.loadAddon(new WebLinksAddon());

        terminal.open(container);

        try {
            const webgl = new WebglAddon();
            webgl.onContextLoss(() => {
                webgl.dispose();
                terminal.loadAddon(new CanvasAddon());
            });
            terminal.loadAddon(webgl);
        } catch {
            terminal.loadAddon(new CanvasAddon());
        }

        const dataListener = terminal.onData((data) => {
            window.Android?.sendInput(sessionId, data);
        });

        const scrollListener = terminal.onScroll(() => this.notifyScroll());

        const entry: SessionEntry = {
            sessionId,
            name,
            serverId,
            terminal,
            fitAddon,
            container,
            disposables: [dataListener, scrollListener],
        };
        this.sessions.set(sessionId, entry);

        return terminal;
    }

    removeSession(sessionId: string): void {
        const entry = this.sessions.get(sessionId);
        if (!entry) return;

        entry.disposables.forEach((d) => d.dispose());
        entry.terminal.dispose();
        entry.container.remove();
        this.sessions.delete(sessionId);

        if (this.activeSessionId === sessionId) {
            const nextId = this.sessions.keys().next().value ?? null;
            if (nextId) {
                this.switchTo(nextId);
            } else {
                this.activeSessionId = null;
            }
        }
    }

    switchTo(sessionId: string): void {
        // Hide current
        if (this.activeSessionId) {
            const current = this.sessions.get(this.activeSessionId);
            if (current) current.container.style.display = "none";
        }

        // Show target
        const target = this.sessions.get(sessionId);
        if (target) {
            target.container.style.display = "block";
            this.activeSessionId = sessionId;

            // Defer fit until after the browser completes layout for the
            // newly-visible container, otherwise dimensions are stale.
            requestAnimationFrame(() => {
                target.fitAddon.fit();
                target.terminal.focus();
                window.Android?.sendResize(
                    sessionId,
                    target.terminal.cols,
                    target.terminal.rows,
                );
            });
        }
    }

    getTerminal(sessionId: string): Terminal | undefined {
        return this.sessions.get(sessionId)?.terminal;
    }

    getActiveSessionId(): string | null {
        return this.activeSessionId;
    }

    getActiveTerminal(): Terminal | undefined {
        if (!this.activeSessionId) return undefined;
        return this.sessions.get(this.activeSessionId)?.terminal;
    }

    getActiveServerId(): string | null {
        if (!this.activeSessionId) return null;
        return this.sessions.get(this.activeSessionId)?.serverId ?? null;
    }

    getSessionCount(): number {
        return this.sessions.size;
    }

    getAllEntries(): Array<{ sessionId: string; name: string }> {
        return Array.from(this.sessions.values()).map((e) => ({
            sessionId: e.sessionId,
            name: e.name,
        }));
    }

    /** Resize the active session's terminal. */
    syncActiveSize(): void {
        if (!this.activeSessionId) return;
        const entry = this.sessions.get(this.activeSessionId);
        if (entry) {
            entry.fitAddon.fit();
            window.Android?.sendResize(
                this.activeSessionId,
                entry.terminal.cols,
                entry.terminal.rows,
            );
        }
    }

    /** Apply theme to all terminal instances. */
    applyThemeToAll(theme: import("./types").ThemeName): void {
        const xtermTheme = getTheme(theme);
        for (const entry of this.sessions.values()) {
            entry.terminal.options.theme = xtermTheme;
        }
    }
}
