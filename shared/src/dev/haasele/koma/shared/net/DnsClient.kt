package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.domain.DnsRecordType
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.random.Random

/**
 * A DNS resolver speaking the wire protocol directly over UDP. Doing this in common code keeps
 * DNS monitors identical on every platform instead of depending on host resolver behaviour.
 */
object DnsClient {

    suspend fun query(
        server: String,
        port: Int,
        name: String,
        recordType: DnsRecordType,
        timeoutMs: Long,
    ): List<String> {
        val transactionId = Random.nextInt(0, 0xFFFF)
        val request = buildQuery(transactionId, name, recordType)

        val selector = SelectorManager(Dispatchers.Default)
        try {
            val socket = aSocket(selector).udp().connect(InetSocketAddress(server, port))
            try {
                return withTimeout(timeoutMs) {
                    socket.send(Datagram(Buffer().apply { write(request) }, InetSocketAddress(server, port)))
                    val response = socket.receive().packet.readByteArray()
                    parseResponse(response, recordType)
                }
            } finally {
                socket.close()
            }
        } finally {
            selector.close()
        }
    }

    internal fun buildQuery(transactionId: Int, name: String, recordType: DnsRecordType): ByteArray {
        val out = ArrayList<Byte>(64)
        fun writeShort(value: Int) {
            out.add(((value shr 8) and 0xff).toByte())
            out.add((value and 0xff).toByte())
        }

        writeShort(transactionId)
        writeShort(0x0100) // standard query, recursion desired
        writeShort(1) // questions
        writeShort(0) // answers
        writeShort(0) // authority
        writeShort(0) // additional

        name.trim('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.encodeToByteArray()
            require(bytes.size <= 63) { "DNS label too long: $label" }
            out.add(bytes.size.toByte())
            bytes.forEach { out.add(it) }
        }
        out.add(0)
        writeShort(recordType.wireType())
        writeShort(1) // IN
        return out.toByteArray()
    }

    internal fun parseResponse(data: ByteArray, recordType: DnsRecordType): List<String> {
        if (data.size < 12) throw DnsException("Truncated DNS response")
        val flags = data.readShort(2)
        val responseCode = flags and 0x0f
        if (responseCode != 0) throw DnsException("DNS server returned ${responseCodeName(responseCode)}")

        val questionCount = data.readShort(4)
        val answerCount = data.readShort(6)
        if (answerCount == 0) return emptyList()

        var offset = 12
        repeat(questionCount) {
            offset = skipName(data, offset)
            offset += 4
        }

        val results = mutableListOf<String>()
        repeat(answerCount) {
            offset = skipName(data, offset)
            if (offset + 10 > data.size) return results
            val type = data.readShort(offset)
            val dataLength = data.readShort(offset + 8)
            val rdataStart = offset + 10
            if (rdataStart + dataLength > data.size) return results

            if (type == recordType.wireType()) {
                results += decodeRecord(data, rdataStart, dataLength, recordType)
            }
            offset = rdataStart + dataLength
        }
        return results
    }

    private fun decodeRecord(data: ByteArray, start: Int, length: Int, recordType: DnsRecordType): String =
        when (recordType) {
            DnsRecordType.A -> (0 until 4).joinToString(".") { (data[start + it].toInt() and 0xff).toString() }
            DnsRecordType.AAAA -> (0 until 8).joinToString(":") { group ->
                val value = ((data[start + group * 2].toInt() and 0xff) shl 8) or
                    (data[start + group * 2 + 1].toInt() and 0xff)
                value.toString(16)
            }
            DnsRecordType.CNAME, DnsRecordType.NS, DnsRecordType.PTR -> readName(data, start).first
            DnsRecordType.MX -> {
                val preference = data.readShort(start)
                "$preference ${readName(data, start + 2).first}"
            }
            DnsRecordType.TXT -> {
                val builder = StringBuilder()
                var cursor = start
                while (cursor < start + length) {
                    val chunk = data[cursor].toInt() and 0xff
                    cursor++
                    builder.append(data.decodeToString(cursor, cursor + chunk))
                    cursor += chunk
                }
                builder.toString()
            }
            DnsRecordType.SOA -> {
                val (primary, afterPrimary) = readName(data, start)
                val (mailbox, afterMailbox) = readName(data, afterPrimary)
                val serial = data.readInt(afterMailbox)
                "$primary $mailbox $serial"
            }
            DnsRecordType.SRV -> {
                val priority = data.readShort(start)
                val weight = data.readShort(start + 2)
                val port = data.readShort(start + 4)
                "$priority $weight $port ${readName(data, start + 6).first}"
            }
            DnsRecordType.CAA -> {
                val tagLength = data[start + 1].toInt() and 0xff
                val tag = data.decodeToString(start + 2, start + 2 + tagLength)
                val value = data.decodeToString(start + 2 + tagLength, start + length)
                "$tag $value"
            }
        }

    private fun readName(data: ByteArray, start: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var offset = start
        var afterPointer = -1
        var guard = 0

        while (offset < data.size && guard++ < 128) {
            val length = data[offset].toInt() and 0xff
            when {
                length == 0 -> {
                    offset++
                    break
                }
                length and 0xc0 == 0xc0 -> {
                    val pointer = ((length and 0x3f) shl 8) or (data[offset + 1].toInt() and 0xff)
                    if (afterPointer < 0) afterPointer = offset + 2
                    offset = pointer
                }
                else -> {
                    labels += data.decodeToString(offset + 1, offset + 1 + length)
                    offset += length + 1
                }
            }
        }
        return labels.joinToString(".") to if (afterPointer >= 0) afterPointer else offset
    }

    private fun skipName(data: ByteArray, start: Int): Int = readName(data, start).second

    private fun ByteArray.readShort(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private fun ByteArray.readInt(offset: Int): Long =
        ((this[offset].toLong() and 0xff) shl 24) or
            ((this[offset + 1].toLong() and 0xff) shl 16) or
            ((this[offset + 2].toLong() and 0xff) shl 8) or
            (this[offset + 3].toLong() and 0xff)

    private fun responseCodeName(code: Int): String = when (code) {
        1 -> "FORMERR"
        2 -> "SERVFAIL"
        3 -> "NXDOMAIN"
        4 -> "NOTIMP"
        5 -> "REFUSED"
        else -> "rcode $code"
    }

    private fun DnsRecordType.wireType(): Int = when (this) {
        DnsRecordType.A -> 1
        DnsRecordType.NS -> 2
        DnsRecordType.CNAME -> 5
        DnsRecordType.SOA -> 6
        DnsRecordType.PTR -> 12
        DnsRecordType.MX -> 15
        DnsRecordType.TXT -> 16
        DnsRecordType.AAAA -> 28
        DnsRecordType.SRV -> 33
        DnsRecordType.CAA -> 257
    }
}

class DnsException(message: String) : Exception(message)
