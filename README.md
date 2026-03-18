# Terminal

A native Android SSH terminal app with a retro 80s aesthetic. Connects directly to any machine running SSH, with tmux session persistence and multi-tab support.

Built for GrapheneOS on Pixel 9a. No Google Play Services required.

## What it does

- SSH terminal with xterm.js rendering (256-color, full VT100)
- Multi-tab sessions to different servers simultaneously
- tmux integration: auto-attaches to named sessions, survives disconnects
- Ed25519 key authentication (keys encrypted with Android Keystore)
- Foreground service keeps connections alive in background
- Retro green/amber phosphor terminal theme
- Push-to-talk mic button (Phase 2: on-device STT)

## Server requirements

The app connects via standard SSH. Your server needs:

### Required

- **OpenSSH server** (`sshd`) running and accessible
- **Ed25519 public key** added to `~/.ssh/authorized_keys`

That's it. No custom server software, no WebSocket proxy, no middleware.

### Recommended

- **tmux** installed for session persistence
  ```
  # Ubuntu/Debian
  sudo apt install tmux

  # The app runs: tmux new-session -As <session-name>
  # If tmux is not installed, it falls back to a plain shell
  ```

### Network access

The phone needs to reach the server's SSH port (default 22). Options:

| Setup | How | When to use |
|---|---|---|
| LAN | Direct IP (e.g., `192.168.1.100:22`) | Phone on same network |
| VPN | WireGuard to home network, use LAN IP | Phone on any network |
| Public | Port forward or nginx TCP proxy to sshd | No VPN available |

### Adding the app's SSH key to your server

1. Open the app, tap the key icon on the home screen
2. Generate a key, copy the public key
3. On your server:
   ```
   echo "ssh-ed25519 AAAA... terminal-app" >> ~/.ssh/authorized_keys
   ```

## Building

### Prerequisites

- Android Studio (or JDK 17+ and Android SDK 35+)
- Node.js 18+ (for the terminal frontend)

### Build

```bash
# Set environment (or add to ~/.zshrc)
export JAVA_HOME=~/tools/android-studio/jbr
export ANDROID_HOME=~/Android/Sdk

# Build (frontend builds automatically via Gradle task)
./gradlew assembleDebug

# APK output
app/build/outputs/apk/debug/app-debug.apk
```

### Install on device/emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run tests

```bash
./gradlew testDebugUnitTest
```

## Architecture

```
Phone                              Server
-----                              ------
xterm.js (WebView)                 sshd
    |                                |
    | JS bridge                      |
    v                                |
TerminalBridge (Kotlin)              |
    |                                |
SessionManager                       |
    |                                |
SshManager --- SSH (encrypted) ----> tmux session
    |                                    |
Foreground Service                   shell (bash/zsh)
(keeps alive in background)
```

### Key components

| Component | Purpose |
|---|---|
| `terminal-frontend/` | Vite + TypeScript xterm.js app, builds into Android assets |
| `SessionManager` | Manages multiple concurrent SSH sessions with isolated coroutine scopes |
| `SshManager` | Single SSH connection lifecycle (connect, I/O, reconnect with exponential backoff) |
| `SshKeyManager` | Ed25519 key generation via sshlib, encrypted at rest with Android Keystore AES-256-GCM |
| `TerminalBridge` | JavaScriptInterface bridge: routes I/O between WebView JS and Kotlin by session ID |
| `TerminalService` | Foreground service (connectedDevice type), hosts all sessions |
| `TmuxHelper` | Builds tmux attach/detach commands, configurable session name per server |

### Frontend structure

```
terminal-frontend/src/
  main.ts           Entry point, wires everything together
  session-manager.ts  Multiple xterm.js Terminal instances, one per tab
  bridge.ts         NativeTerminal API (Kotlin calls these via evaluateJavascript)
  input.ts          Text input, toolbar, floating keys, mic/send button
  tabs.ts           Tab bar rendering
  themes.ts         Green/amber retro terminal color schemes
  ui.ts             Theme switching, viewport tracking, touch scrolling
  types.ts          TypeScript interfaces for the JS-Kotlin bridge
  styles.css        Retro 80s terminal aesthetic with scanline effect
```

## Project structure

```
mobile-access-android/
  app/src/main/
    kotlin/xyz/fivekov/terminal/
      ui/           HomeActivity, TerminalActivity, ServerEditActivity, TerminalBridge, ServerAdapter
      ssh/          SshManager, SshKeyManager, SessionManager, TmuxHelper, KeyEncryption
      service/      TerminalService (foreground), QuickSettingsTile
      data/         ServerConfig, ServerRepository, AppPreferences, SessionInfo
      di/           Koin dependency injection module
    res/            Layouts, drawables, strings, themes (retro terminal style)
  app/src/test/     Unit tests (Robolectric + MockK)
  terminal-frontend/  Vite + TypeScript xterm.js frontend
  gradle/           Version catalog, wrapper
```

## Roadmap

- **Phase 2**: On-device voice input (sherpa-onnx, offline STT)
- **Phase 3**: ntfy push notifications, settings screen, Quick Settings tile improvements

## License

Apache 2.0
