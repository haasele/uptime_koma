package dev.haasele.koma.shared.crypto

import kotlin.random.Random

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val builder = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                builder.append(ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1f])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) builder.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1f])
        return builder.toString()
    }

    fun decode(encoded: String): ByteArray {
        val cleaned = encoded.trim().trimEnd('=').uppercase().replace(" ", "")
        val output = ArrayList<Byte>(cleaned.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (char in cleaned) {
            val index = ALPHABET.indexOf(char)
            require(index >= 0) { "Invalid base32 character: $char" }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xff).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }
}

/** RFC 6238 time based one time passwords, used for the optional second factor. */
object Totp {
    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6

    fun generateSecret(bytes: Int = 20): String = Base32.encode(Random.Default.nextBytes(bytes))

    fun code(secret: String, epochSeconds: Long): String {
        val counter = epochSeconds / PERIOD_SECONDS
        val counterBytes = ByteArray(8) { ((counter ushr ((7 - it) * 8)) and 0xff).toByte() }
        val mac = hmac(Sha1, Base32.decode(secret), counterBytes)
        val offset = mac[mac.size - 1].toInt() and 0x0f
        val binary = ((mac[offset].toInt() and 0x7f) shl 24) or
            ((mac[offset + 1].toInt() and 0xff) shl 16) or
            ((mac[offset + 2].toInt() and 0xff) shl 8) or
            (mac[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /** Accepts the neighbouring windows so a slightly skewed device clock still works. */
    fun verify(secret: String, code: String, epochSeconds: Long, window: Int = 1): Boolean {
        val normalized = code.trim()
        for (drift in -window..window) {
            if (code(secret, epochSeconds + drift * PERIOD_SECONDS) == normalized) return true
        }
        return false
    }

    fun provisioningUri(secret: String, account: String, issuer: String = "Uptime Koma"): String =
        "otpauth://totp/${issuer.encodeUriComponent()}:${account.encodeUriComponent()}" +
            "?secret=$secret&issuer=${issuer.encodeUriComponent()}&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
}

private fun String.encodeUriComponent(): String = buildString {
    for (char in this@encodeUriComponent) {
        when {
            char.isLetterOrDigit() || char in "-_.~" -> append(char)
            else -> char.toString().encodeToByteArray().forEach { byte ->
                append('%').append((byte.toInt() and 0xff).toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
