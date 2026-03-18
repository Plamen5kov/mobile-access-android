package xyz.fivekov.terminal.ssh

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import xyz.fivekov.terminal.data.AppPreferences

/** Pass-through encryption for testing (no Keystore needed). */
class FakeKeyEncryption : KeyEncryption {
    override fun encrypt(data: ByteArray) = EncryptedData(data, byteArrayOf(0))
    override fun decrypt(encrypted: EncryptedData) = encrypted.ciphertext
}

@RunWith(RobolectricTestRunner::class)
class SshKeyManagerTest {

    private lateinit var keyManager: SshKeyManager

    @Before
    fun setup() {
        stopKoin()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        prefs.secure.edit().clear().commit()
        keyManager = SshKeyManager(prefs, FakeKeyEncryption())
    }

    @Test
    fun `hasKeyPair returns false before generation`() {
        assertFalse(keyManager.hasKeyPair())
    }

    @Test
    fun `generateKeyPair creates a key pair`() {
        keyManager.generateKeyPair()
        assertTrue(keyManager.hasKeyPair())
    }

    @Test
    fun `getPublicKeyOpenSsh returns null before generation`() {
        assertNull(keyManager.getPublicKeyOpenSsh())
    }

    @Test
    fun `getPublicKeyOpenSsh returns valid OpenSSH format`() {
        keyManager.generateKeyPair()
        val pubKey = keyManager.getPublicKeyOpenSsh()

        assertNotNull(pubKey)
        assertTrue(pubKey!!.startsWith("ssh-ed25519 "))
        assertTrue(pubKey.endsWith(" terminal-app"))

        val parts = pubKey.split(" ")
        assertEquals(3, parts.size)
        assertEquals("ssh-ed25519", parts[0])
        assertEquals("terminal-app", parts[2])
    }

    @Test
    fun `getKeyPair returns null before generation`() {
        assertNull(keyManager.getKeyPair())
    }

    @Test
    fun `getKeyPair returns valid Ed25519 key pair after generation`() {
        keyManager.generateKeyPair()
        val keyPair = keyManager.getKeyPair()

        assertNotNull(keyPair)
        assertTrue(keyPair!!.private is Ed25519PrivateKey)
        assertTrue(keyPair.public is Ed25519PublicKey)
    }

    @Test
    fun `key pair persists across manager instances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AppPreferences(context)
        val encryption = FakeKeyEncryption()

        val manager1 = SshKeyManager(prefs, encryption)
        manager1.generateKeyPair()
        val pubKey1 = manager1.getPublicKeyOpenSsh()

        val manager2 = SshKeyManager(prefs, encryption)
        assertTrue(manager2.hasKeyPair())
        assertEquals(pubKey1, manager2.getPublicKeyOpenSsh())
    }

    @Test
    fun `regenerating key pair produces different keys`() {
        keyManager.generateKeyPair()
        val pubKey1 = keyManager.getPublicKeyOpenSsh()

        keyManager.generateKeyPair()
        val pubKey2 = keyManager.getPublicKeyOpenSsh()

        assertTrue(pubKey1 != pubKey2)
    }

    @Test
    fun `getKeyPair round-trips correctly`() {
        keyManager.generateKeyPair()
        val keyPair = keyManager.getKeyPair()

        assertNotNull(keyPair)
        val pubBytes = (keyPair!!.public as Ed25519PublicKey).abyte
        assertTrue(pubBytes.size == 32)
    }
}
