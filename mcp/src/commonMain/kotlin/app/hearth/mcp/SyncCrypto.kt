package app.hearth.mcp

/**
 * Sealing for sync, from the platform: AES-256-GCM (a random 12-byte nonce in front of the box) and PBKDF2
 * (SHA-256) for join codes. javax.crypto on Android and the JVM, WebCrypto in the browser.
 */
internal expect object SyncCrypto {
    fun random(size: Int): ByteArray
    suspend fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray
    /** Throws when the box was made with another key or changed on the way. */
    suspend fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray
    /** 32 bytes from [secret], slowly on purpose. */
    suspend fun derive(secret: String, salt: String, iterations: Int): ByteArray
}
