package xyz.fivekov.terminal.testutil

import xyz.fivekov.terminal.ssh.EncryptedData
import xyz.fivekov.terminal.ssh.KeyEncryption

/** Pass-through encryption for testing (no Android Keystore needed). */
class FakeKeyEncryption : KeyEncryption {
    override fun encrypt(data: ByteArray) = EncryptedData(data, byteArrayOf(0))
    override fun decrypt(encrypted: EncryptedData) = encrypted.ciphertext
}
