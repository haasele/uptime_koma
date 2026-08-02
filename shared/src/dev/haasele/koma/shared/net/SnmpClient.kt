package dev.haasele.koma.shared.net

import kotlin.random.Random

class SnmpException(message: String) : Exception(message)

/**
 * SNMP v1/v2c GET over UDP with just enough BER to encode a request and read a single varbind.
 * Anything beyond a scalar OID lookup is out of scope; that is also all Uptime Koma queries.
 */
object SnmpClient {

    private const val TAG_INTEGER = 0x02
    private const val TAG_OCTET_STRING = 0x04
    private const val TAG_NULL = 0x05
    private const val TAG_OID = 0x06
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_GET_REQUEST = 0xA0

    suspend fun get(
        host: String,
        port: Int,
        community: String,
        oid: String,
        version2c: Boolean,
        timeoutMs: Long,
    ): String {
        val requestId = Random.nextInt(1, Int.MAX_VALUE)
        val varbind = tlv(
            TAG_SEQUENCE,
            tlv(TAG_SEQUENCE, tlv(TAG_OID, encodeOid(oid)) + tlv(TAG_NULL, ByteArray(0))),
        )
        val pdu = tlv(
            TAG_GET_REQUEST,
            tlv(TAG_INTEGER, encodeInteger(requestId)) +
                tlv(TAG_INTEGER, encodeInteger(0)) +
                tlv(TAG_INTEGER, encodeInteger(0)) +
                varbind,
        )
        val message = tlv(
            TAG_SEQUENCE,
            tlv(TAG_INTEGER, encodeInteger(if (version2c) 1 else 0)) +
                tlv(TAG_OCTET_STRING, community.encodeToByteArray()) +
                pdu,
        )

        val response = udpExchange(host, port, message, timeoutMs)
        if (response.isEmpty()) throw SnmpException("No response from the agent")
        return readVarbindValue(response)
    }

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val length = encodeLength(content.size)
        val out = ByteArray(1 + length.size + content.size)
        out[0] = tag.toByte()
        length.copyInto(out, 1)
        content.copyInto(out, 1 + length.size)
        return out
    }

    private fun encodeLength(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = mutableListOf<Byte>()
        var remaining = length
        while (remaining > 0) {
            bytes.add(0, remaining.toByte())
            remaining = remaining ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun encodeInteger(value: Int): ByteArray {
        val bytes = mutableListOf<Byte>()
        var remaining = value
        do {
            bytes.add(0, remaining.toByte())
            remaining = remaining shr 8
        } while (remaining != 0 && remaining != -1)
        if (value > 0 && (bytes[0].toInt() and 0x80) != 0) bytes.add(0, 0)
        return bytes.toByteArray()
    }

    private fun encodeOid(oid: String): ByteArray {
        val parts = oid.trim().removePrefix(".").split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) throw SnmpException("Invalid OID: $oid")
        val out = mutableListOf<Byte>()
        out.add((parts[0] * 40 + parts[1]).toByte())
        parts.drop(2).forEach { component ->
            val chunk = mutableListOf<Byte>()
            var remaining = component
            do {
                chunk.add(0, (remaining and 0x7f).toByte())
                remaining = remaining ushr 7
            } while (remaining > 0)
            for (i in 0 until chunk.size - 1) chunk[i] = (chunk[i].toInt() or 0x80).toByte()
            out.addAll(chunk)
        }
        return out.toByteArray()
    }

    /**
     * Walks into the nested sequences and returns the value of the first varbind. The structure is
     * fixed for a GET response, so a targeted descent is clearer than a general purpose parser.
     */
    private fun readVarbindValue(response: ByteArray): String {
        var cursor = Cursor(response, 0)
        cursor = cursor.enter(TAG_SEQUENCE)
        cursor.skipField() // version
        cursor.skipField() // community
        cursor = cursor.enterAny() // response PDU
        cursor.skipField() // request id
        val errorStatus = cursor.readIntField()
        cursor.skipField() // error index
        if (errorStatus != 0) throw SnmpException("Agent reported error status $errorStatus")
        cursor = cursor.enter(TAG_SEQUENCE) // varbind list
        cursor = cursor.enter(TAG_SEQUENCE) // varbind
        cursor.skipField() // oid
        return cursor.readValue()
    }

    private class Cursor(val bytes: ByteArray, var offset: Int) {
        fun enter(tag: Int): Cursor {
            val actual = bytes[offset].toInt() and 0xff
            if (actual != tag) throw SnmpException("Malformed response")
            offset++
            readLength()
            return this
        }

        fun enterAny(): Cursor {
            offset++
            readLength()
            return this
        }

        fun skipField() {
            offset++
            val length = readLength()
            offset += length
        }

        fun readIntField(): Int {
            offset++
            val length = readLength()
            var value = 0
            repeat(length) { value = (value shl 8) or (bytes[offset++].toInt() and 0xff) }
            return value
        }

        fun readValue(): String {
            val tag = bytes[offset++].toInt() and 0xff
            val length = readLength()
            val content = bytes.copyOfRange(offset, minOf(bytes.size, offset + length))
            offset += length
            return when (tag) {
                TAG_OCTET_STRING -> content.decodeToString()
                TAG_NULL -> throw SnmpException("The OID has no value on this agent")
                TAG_OID -> content.joinToString(".") { (it.toInt() and 0xff).toString() }
                else -> {
                    var value = 0L
                    content.forEach { value = (value shl 8) or (it.toLong() and 0xff) }
                    value.toString()
                }
            }
        }

        private fun readLength(): Int {
            val first = bytes[offset++].toInt() and 0xff
            if (first < 0x80) return first
            var value = 0
            repeat(first and 0x7f) { value = (value shl 8) or (bytes[offset++].toInt() and 0xff) }
            return value
        }
    }
}
