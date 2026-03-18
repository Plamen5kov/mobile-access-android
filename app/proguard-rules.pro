# sshlib - keep SSH connection classes
-keep class com.trilead.ssh2.** { *; }
-keep class org.connectbot.simplesocks.** { *; }

# Keep JS bridge interface methods
-keepclassmembers class xyz.fivekov.terminal.ui.TerminalBridge {
    @android.webkit.JavascriptInterface <methods>;
}
