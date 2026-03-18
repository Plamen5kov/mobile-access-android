package xyz.fivekov.terminal.ssh

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts/decrypts SSH key material using Android Keystore. */
interface KeyEncryption {
    fun encrypt(data: ByteArray): EncryptedData
    fun decrypt(encrypted: EncryptedData): ByteArray
}

data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray)

/** Production implementation backed by hardware Keystore. */
class KeystoreEncryption(private val alias: String) : KeyEncryption {

    companion object {
        private const val GCM_TAG_LENGTH = 128
    }

    override fun encrypt(data: ByteArray): EncryptedData {
        ensureKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        return EncryptedData(cipher.doFinal(data), cipher.iv)
    }

    override fun decrypt(encrypted: EncryptedData): ByteArray {
        val key = getKey() ?: throw IllegalStateException("Keystore key not found")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH, encrypted.iv)
        )
        return cipher.doFinal(encrypted.ciphertext)
    }

    private fun ensureKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) return

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        keyGen.generateKey()
    }

    private fun getKey(): SecretKey? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        return entry?.secretKey
    }
}
