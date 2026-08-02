package dev.haasele.koma.shared.net

data class SteamServerInfo(
    val name: String,
    val map: String,
    val players: Int,
    val maxPlayers: Int,
)

/** A2S_INFO, including the challenge handshake newer Source servers require. */
object SteamQuery {

    private val header = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

    suspend fun info(host: String, port: Int, timeoutMs: Long): SteamServerInfo {
        var response = udpExchange(host, port, requestPacket(null), timeoutMs)

        if (response.size >= 5 && response[4] == 0x41.toByte()) {
            val challenge = response.copyOfRange(5, minOf(9, response.size))
            response = udpExchange(host, port, requestPacket(challenge), timeoutMs)
        }

        require(response.size > 6 && response[4] == 0x49.toByte()) { "Server did not answer A2S_INFO" }

        var cursor = 6 // skip header and protocol version
        val name = readString(response, cursor).also { cursor += it.length + 1 }
        val map = readString(response, cursor).also { cursor += it.length + 1 }
        val folder = readString(response, cursor).also { cursor += it.length + 1 }
        val game = readString(response, cursor).also { cursor += it.length + 1 }
        cursor += 2 // app id
        val players = response.getOrElse(cursor) { 0 }.toInt() and 0xff
        val maxPlayers = response.getOrElse(cursor + 1) { 0 }.toInt() and 0xff

        return SteamServerInfo(
            name = name.ifBlank { game.ifBlank { folder } },
            map = map,
            players = players,
            maxPlayers = maxPlayers,
        )
    }

    private fun requestPacket(challenge: ByteArray?): ByteArray {
        val body = byteArrayOf(0x54) + "Source Engine Query".encodeToByteArray() + byteArrayOf(0)
        return header + body + (challenge ?: ByteArray(0))
    }

    private fun readString(data: ByteArray, start: Int): String {
        val end = (start until data.size).firstOrNull { data[it].toInt() == 0 } ?: data.size
        return data.decodeToString(start, end)
    }
}
