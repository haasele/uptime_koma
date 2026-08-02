package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.crypto.Md5
import kotlin.random.Random

enum class RadiusReply(val code: Int, val label: String) {
    ACCEPT(2, "Access-Accept"),
    REJECT(3, "Access-Reject"),
    CHALLENGE(11, "Access-Challenge"),
    UNKNOWN(-1, "Unknown response");

    companion object {
        fun fromCode(code: Int): RadiusReply = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Minimal RFC 2865 Access-Request client. Only the attributes Uptime Koma exposes are supported,
 * which is enough to prove that a RADIUS server authenticates a known account.
 */
object RadiusClient {

    private const val ACCESS_REQUEST = 1
    private const val ATTR_USER_NAME = 1
    private const val ATTR_USER_PASSWORD = 2
    private const val ATTR_CALLED_STATION_ID = 30
    private const val ATTR_CALLING_STATION_ID = 31

    suspend fun authenticate(
        host: String,
        port: Int,
        secret: String,
        username: String,
        password: String,
        calledStationId: String,
        callingStationId: String,
        timeoutMs: Long,
    ): RadiusReply {
        val identifier = Random.nextInt(256)
        val authenticator = ByteArray(16).also { Random.nextBytes(it) }

        val attributes = buildList {
            add(attribute(ATTR_USER_NAME, username.encodeToByteArray()))
            add(attribute(ATTR_USER_PASSWORD, encryptPassword(password, secret, authenticator)))
            if (calledStationId.isNotBlank()) add(attribute(ATTR_CALLED_STATION_ID, calledStationId.encodeToByteArray()))
            if (callingStationId.isNotBlank()) {
                add(attribute(ATTR_CALLING_STATION_ID, callingStationId.encodeToByteArray()))
            }
        }.reduceOrNull { a, b -> a + b } ?: ByteArray(0)

        val length = 20 + attributes.size
        val packet = ByteArray(length)
        packet[0] = ACCESS_REQUEST.toByte()
        packet[1] = identifier.toByte()
        packet[2] = (length ushr 8).toByte()
        packet[3] = length.toByte()
        authenticator.copyInto(packet, 4)
        attributes.copyInto(packet, 20)

        val response = udpExchange(host, port, packet, timeoutMs)
        if (response.size < 4) return RadiusReply.UNKNOWN
        return RadiusReply.fromCode(response[0].toInt() and 0xff)
    }

    private fun attribute(type: Int, value: ByteArray): ByteArray {
        val out = ByteArray(2 + value.size)
        out[0] = type.toByte()
        out[1] = (2 + value.size).toByte()
        value.copyInto(out, 2)
        return out
    }

    /** RFC 2865 §5.2: the password is XORed with a chain of MD5 digests over the shared secret. */
    private fun encryptPassword(password: String, secret: String, authenticator: ByteArray): ByteArray {
        val raw = password.encodeToByteArray()
        val blocks = maxOf(1, (raw.size + 15) / 16)
        val padded = ByteArray(blocks * 16).also { raw.copyInto(it) }
        val secretBytes = secret.encodeToByteArray()
        val out = ByteArray(padded.size)

        var previous = authenticator
        for (block in 0 until blocks) {
            val digest = Md5.digest(secretBytes + previous)
            val chunk = ByteArray(16)
            for (i in 0 until 16) {
                chunk[i] = (padded[block * 16 + i].toInt() xor digest[i].toInt()).toByte()
            }
            chunk.copyInto(out, block * 16)
            previous = chunk
        }
        return out
    }
}
