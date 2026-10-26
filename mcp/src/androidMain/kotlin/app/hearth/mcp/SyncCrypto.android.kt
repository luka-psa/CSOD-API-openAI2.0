package app.hearth.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal actual object SyncCrypto {
    private val rng = SecureRandom()

    actual fun random(size: Int): ByteArray = ByteArray(size).also(rng::nextBytes)

    actual suspend fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val iv = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        iv + cipher.doFinal(plain)
    }

    actual suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, 12))
        cipher.updateAAD(aad)
        cipher.doFinal(sealed, 12, sealed.size - 12)
    }

    actual suspend fun derive(secret: String, salt: String, iterations: Int): ByteArray = withContext(Dispatchers.Default) {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret.toCharArray(), salt.encodeToByteArray(), iterations, 256)).encoded
    }
}
