import type { ITheme } from "@xterm/xterm";
import type { ThemeName } from "./types";

export const greenTheme: ITheme = {
    background: "#0a0a0a",
    foreground: "#33ff33",
    cursor: "#33ff33",
    cursorAccent: "#0a0a0a",
    selectionBackground: "rgba(51, 255, 51, 0.2)",
    black: "#0a0a0a",
    red: "#ff3333",
    green: "#33ff33",
    yellow: "#cccc00",
    blue: "#33ccff",
    magenta: "#cc66ff",
    cyan: "#33ffcc",
    white: "#33ff33",
    brightBlack: "#1a5a1a",
    brightRed: "#ff6666",
    brightGreen: "#66ff66",
    brightYellow: "#ffff33",
    brightBlue: "#66ddff",
    brightMagenta: "#dd99ff",
    brightCyan: "#66ffdd",
    brightWhite: "#ccffcc",
};

export const amberTheme: ITheme = {
    background: "#0a0a0a",
    foreground: "#ffb000",
    cursor: "#ffb000",
    cursorAccent: "#0a0a0a",
    selectionBackground: "rgba(255, 176, 0, 0.2)",
    black: "#0a0a0a",
    red: "#ff3333",
    green: "#ffb000",
    yellow: "#ffdd00",
    blue: "#ffcc66",
    magenta: "#ff9966",
    cyan: "#ffcc99",
    white: "#ffb000",
    brightBlack: "#604000",
    brightRed: "#ff6666",
    brightGreen: "#ffcc33",
    brightYellow: "#ffee66",
    brightBlue: "#ffdd99",
    brightMagenta: "#ffbb88",
    brightCyan: "#ffddbb",
    brightWhite: "#ffe0aa",
};

export function getTheme(name: ThemeName): ITheme {
    return name === "amber" ? amberTheme : greenTheme;
}
