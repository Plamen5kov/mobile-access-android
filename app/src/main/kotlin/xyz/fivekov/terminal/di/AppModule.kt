package xyz.fivekov.terminal.di

import xyz.fivekov.terminal.data.AppPreferences
import xyz.fivekov.terminal.data.ServerRepository
import xyz.fivekov.terminal.ssh.KeyEncryption
import xyz.fivekov.terminal.ssh.KeystoreEncryption
import xyz.fivekov.terminal.ssh.SessionManager
import xyz.fivekov.terminal.ssh.SshKeyManager
import xyz.fivekov.terminal.ssh.TmuxHelper
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { AppPreferences(androidContext()) }
    single { ServerRepository(get()) }
    single<KeyEncryption> { KeystoreEncryption("terminal_ssh_key_encryption") }
    single { SshKeyManager(get(), get()) }
    single { TmuxHelper() }
    single { SessionManager(get(), get()) }
}
