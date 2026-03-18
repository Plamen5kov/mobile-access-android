package xyz.fivekov.terminal.ssh

import com.trilead.ssh2.crypto.keys.Ed25519KeyPairGenerator
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import xyz.fivekov.terminal.data.AppPreferences
import java.security.KeyPair
import java.security.SecureRandom
import java.util.Base64

class SshKeyManager(
    private val prefs: AppPreferences,
    private val encryption: KeyEncryption,
) {
    companion object {
        private const val KEY_ENCRYPTED_SEED = "ssh_ed25519_encrypted_seed"
        private const val KEY_SEED_IV = "ssh_ed25519_seed_iv"
        private const val KEY_PUBLIC = "ssh_ed25519_public"
    }

    fun hasKeyPair(): Boolean {
        return prefs.secure.contains(KEY_ENCRYPTED_SEED) && prefs.secure.contains(KEY_PUBLIC)
    }

    fun generateKeyPair() {
        val generator = Ed25519KeyPairGenerator()
        generator.initialize(256, SecureRandom())
        val keyPair = generator.generateKeyPair()

        val privateKey = keyPair.private as Ed25519PrivateKey
        val publicKey = keyPair.public as Ed25519PublicKey

        val encrypted = encryption.encrypt(privateKey.seed)

        prefs.secure.edit()
            .putString(KEY_ENCRYPTED_SEED, Base64.getEncoder().encodeToString(encrypted.ciphertext))
            .putString(KEY_SEED_IV, Base64.getEncoder().encodeToString(encrypted.iv))
            .putString(KEY_PUBLIC, Base64.getEncoder().encodeToString(publicKey.abyte))
            .apply()
    }

    fun getKeyPair(): KeyPair? {
        val encSeedB64 = prefs.secure.getString(KEY_ENCRYPTED_SEED, null) ?: return null
        val ivB64 = prefs.secure.getString(KEY_SEED_IV, null) ?: return null
        val pubB64 = prefs.secure.getString(KEY_PUBLIC, null) ?: return null

        val encrypted = EncryptedData(
            ciphertext = Base64.getDecoder().decode(encSeedB64),
            iv = Base64.getDecoder().decode(ivB64),
        )

        val seed = encryption.decrypt(encrypted)
        val pubBytes = Base64.getDecoder().decode(pubB64)

        return KeyPair(Ed25519PublicKey(pubBytes), Ed25519PrivateKey(seed))
    }

    fun getPublicKeyOpenSsh(): String? {
        val pubB64 = prefs.secure.getString(KEY_PUBLIC, null) ?: return null
        val rawPublicKey = Base64.getDecoder().decode(pubB64)

        val keyType = "ssh-ed25519"
        val wireFormat = buildOpenSshBlob(keyType.toByteArray(), rawPublicKey)
        val sshB64 = Base64.getEncoder().encodeToString(wireFormat)

        return "$keyType $sshB64 terminal-app"
    }

    private fun buildOpenSshBlob(keyType: ByteArray, rawKey: ByteArray): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        buf.writeInt(keyType.size)
        buf.write(keyType)
        buf.writeInt(rawKey.size)
        buf.write(rawKey)
        return buf.toByteArray()
    }

    private fun java.io.ByteArrayOutputStream.writeInt(value: Int) {
        write((value shr 24) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }
}
