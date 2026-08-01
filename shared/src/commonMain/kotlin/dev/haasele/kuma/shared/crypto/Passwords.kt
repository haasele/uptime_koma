package dev.haasele.koma.shared.crypto

import kotlin.random.Random

/**
 * Password hashes are stored as `pbkdf2$sha256$<iterations>$<saltHex>$<hashHex>` so the
 * iteration count can be raised later without invalidating existing credentials.
 */
object Passwords {
    private const val PREFIX = "pbkdf2"
    private const val ALGORITHM = "sha256"
    private const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_LENGTH = 16
    private const val KEY_LENGTH = 32

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = randomBytes(SALT_LENGTH)
        val derived = pbkdf2(password.encodeToByteArray(), salt, iterations, KEY_LENGTH)
        return listOf(PREFIX, ALGORITHM, iterations.toString(), salt.toHex(), derived.toHex()).joinToString("$")
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split("$")
        if (parts.size != 5 || parts[0] != PREFIX || parts[1] != ALGORITHM) return false
        val iterations = parts[2].toIntOrNull() ?: return false
        val salt = runCatching { parts[3].hexToBytes() }.getOrNull() ?: return false
        val expected = runCatching { parts[4].hexToBytes() }.getOrNull() ?: return false
        val actual = pbkdf2(password.encodeToByteArray(), salt, iterations, expected.size)
        return constantTimeEquals(expected, actual)
    }

    fun needsRehash(encoded: String): Boolean {
        val iterations = encoded.split("$").getOrNull(2)?.toIntOrNull() ?: return true
        return iterations < DEFAULT_ITERATIONS
    }
}

fun randomBytes(size: Int): ByteArray = Random.Default.nextBytes(size)

private const val TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomToken(length: Int = 32): String =
    (0 until length).map { TOKEN_ALPHABET[Random.Default.nextInt(TOKEN_ALPHABET.length)] }.joinToString("")
