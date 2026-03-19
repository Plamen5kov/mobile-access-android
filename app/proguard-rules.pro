# Missing annotation classes referenced by dependencies
-dontwarn com.google.errorprone.annotations.**

# sshlib - keep SSH connection classes
-keep class com.trilead.ssh2.** { *; }
-keep class org.connectbot.simplesocks.** { *; }

# Keep JS bridge interface methods
-keepclassmembers class xyz.fivekov.terminal.ui.TerminalBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Koin DI - prevent obfuscation of injected classes
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep interface kotlin.coroutines.** { *; }

# Preserve stack trace line numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
