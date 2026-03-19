import type { ITheme } from "@xterm/xterm";
import type { ThemeName } from "./types";

/** Dark: green phosphor on black CRT */
export const darkTheme: ITheme = {
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

/** Light: amber/brown on warm cream, like an 80s paper terminal */
export const lightTheme: ITheme = {
    background: "#e8dcc8",
    foreground: "#3d2b00",
    cursor: "#cc7700",
    cursorAccent: "#e8dcc8",
    selectionBackground: "rgba(204, 119, 0, 0.2)",
    black: "#3d2b00",
    red: "#aa2200",
    green: "#556b2f",
    yellow: "#8b6914",
    blue: "#4a6a8a",
    magenta: "#7a4a6a",
    cyan: "#4a7a6a",
    white: "#5a4a30",
    brightBlack: "#7a5a1a",
    brightRed: "#cc3300",
    brightGreen: "#6b8e23",
    brightYellow: "#b8860b",
    brightBlue: "#5a8aaa",
    brightMagenta: "#9a6a8a",
    brightCyan: "#5a9a8a",
    brightWhite: "#e8dcc8",
};

export function getTheme(name: ThemeName): ITheme {
    return name === "light" ? lightTheme : darkTheme;
}
