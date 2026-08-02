package dev.haasele.koma.shared.net

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Just enough MQTT 3.1.1 to prove a broker is alive: CONNECT, optionally SUBSCRIBE and read the
 * first retained or live PUBLISH on the topic.
 */
object MqttProbe {

    suspend fun connectAndRead(
        host: String,
        port: Int,
        username: String?,
        password: String?,
        topic: String?,
        useTls: Boolean,
        timeoutMs: Long,
    ): String? = useTcp(host, port, timeoutMs, useTls) { connection ->
        connection.send(connectPacket(username, password))
        val connAck = connection.receive(8)
        require(connAck.size >= 4 && (connAck[0].toInt() and 0xf0) == 0x20) { "Broker did not send CONNACK" }
        val returnCode = connAck[3].toInt() and 0xff
        require(returnCode == 0) { "Broker refused the connection: ${connAckReason(returnCode)}" }

        if (topic == null) return@useTcp null

        connection.send(subscribePacket(topic))
        val subAck = connection.receive(8)
        require(subAck.isNotEmpty() && (subAck[0].toInt() and 0xf0) == 0x90) { "Broker did not confirm SUBSCRIBE" }

        withTimeoutOrNull(timeoutMs) {
            while (true) {
                val packet = connection.receive(8192)
                if (packet.isEmpty()) return@withTimeoutOrNull null
                if ((packet[0].toInt() and 0xf0) == 0x30) return@withTimeoutOrNull readPublishPayload(packet)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    private fun connectPacket(username: String?, password: String?): ByteArray {
        val payload = mutableListOf<Byte>()
        fun writeString(value: String) {
            val bytes = value.encodeToByteArray()
            payload.add((bytes.size shr 8).toByte())
            payload.add(bytes.size.toByte())
            bytes.forEach { payload.add(it) }
        }

        writeString("MQTT")
        payload.add(4) // protocol level 3.1.1

        var flags = 0x02 // clean session
        if (username != null) flags = flags or 0x80
        if (password != null) flags = flags or 0x40
        payload.add(flags.toByte())
        payload.add(0) // keep alive high byte
        payload.add(60) // keep alive low byte

        writeString("koma-native-${(0..9999).random()}")
        username?.let { writeString(it) }
        password?.let { writeString(it) }

        return byteArrayOf(0x10) + encodeRemainingLength(payload.size) + payload.toByteArray()
    }

    private fun subscribePacket(topic: String): ByteArray {
        val payload = mutableListOf<Byte>()
        payload.add(0) // packet id high
        payload.add(1) // packet id low
        val bytes = topic.encodeToByteArray()
        payload.add((bytes.size shr 8).toByte())
        payload.add(bytes.size.toByte())
        bytes.forEach { payload.add(it) }
        payload.add(0) // QoS 0
        return byteArrayOf(0x82.toByte()) + encodeRemainingLength(payload.size) + payload.toByteArray()
    }

    private fun readPublishPayload(packet: ByteArray): String {
        var index = 1
        var multiplier = 1
        var remainingLength = 0
        do {
            val digit = packet[index].toInt() and 0xff
            remainingLength += (digit and 0x7f) * multiplier
            multiplier *= 128
            index++
        } while (digit and 0x80 != 0 && index < packet.size)

        val topicLength = ((packet[index].toInt() and 0xff) shl 8) or (packet[index + 1].toInt() and 0xff)
        val payloadStart = index + 2 + topicLength
        val payloadEnd = minOf(index + remainingLength, packet.size)
        if (payloadStart >= payloadEnd) return ""
        return packet.decodeToString(payloadStart, payloadEnd)
    }

    private fun encodeRemainingLength(length: Int): ByteArray {
        val out = mutableListOf<Byte>()
        var value = length
        do {
            var digit = value % 128
            value /= 128
            if (value > 0) digit = digit or 0x80
            out.add(digit.toByte())
        } while (value > 0)
        return out.toByteArray()
    }

    private fun connAckReason(code: Int): String = when (code) {
        1 -> "unacceptable protocol version"
        2 -> "identifier rejected"
        3 -> "server unavailable"
        4 -> "bad username or password"
        5 -> "not authorized"
        else -> "code $code"
    }
}
