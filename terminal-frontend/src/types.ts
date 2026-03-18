/** Methods exposed by Kotlin's JavaScriptInterface as `window.Android` */
export interface AndroidBridge {
    sendInput(sessionId: string, data: string): void;
    sendResize(sessionId: string, cols: number, rows: number): void;
    startListening(): void;
    stopListening(): void;
    requestReconnect(sessionId: string): void;
    destroySession(sessionId: string): void;
    openSettings(): void;
    openServerSettings(serverId: string): void;
    onWebViewReady(): void;
    onThemeChanged(theme: string): void;
}

/** Methods callable from Kotlin via evaluateJavascript */
export interface NativeTerminalApi {
    writeToTerminal(sessionId: string, data: string): void;
    setConnectionStatus(sessionId: string, status: string, state: string): void;
    addTab(sessionId: string, name: string, serverId: string): void;
    removeTab(sessionId: string): void;
    setActiveTab(sessionId: string): void;
    insertTranscript(text: string, isFinal: boolean): void;
    clearTerminal(): void;
    setTheme(theme: ThemeName): void;
}

export type ThemeName = "green" | "amber";

declare global {
    interface Window {
        Android?: AndroidBridge;
        NativeTerminal: NativeTerminalApi;
    }
}
