package dev.haasele.koma.shared.crypto

/**
 * Minimal hash primitives implemented in common Kotlin so password hashing, TOTP and
 * protocol handshakes behave identically on every target without a native crypto dependency.
 */
interface Digest {
    val blockSize: Int
    val digestSize: Int
    fun digest(message: ByteArray): ByteArray
}

object Sha256 : Digest {
    override val blockSize: Int = 64
    override val digestSize: Int = 32

    private val k = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    override fun digest(message: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
            0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
        )
        val padded = padMessage(message, bigEndianLength = true)
        val w = IntArray(64)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val b = offset + i * 4
                w[i] = (padded[b].toInt() and 0xff shl 24) or
                    (padded[b + 1].toInt() and 0xff shl 16) or
                    (padded[b + 2].toInt() and 0xff shl 8) or
                    (padded[b + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + s1 + ch + k[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                hh = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }

            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            offset += 64
        }

        val out = ByteArray(32)
        for (i in h.indices) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }
}

object Sha1 : Digest {
    override val blockSize: Int = 64
    override val digestSize: Int = 20

    override fun digest(message: ByteArray): ByteArray {
        val h = intArrayOf(0x67452301, -0x10325477, -0x67452302, 0x10325476, -0x3c2d1e10)
        val padded = padMessage(message, bigEndianLength = true)
        val w = IntArray(80)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val b = offset + i * 4
                w[i] = (padded[b].toInt() and 0xff shl 24) or
                    (padded[b + 1].toInt() and 0xff shl 16) or
                    (padded[b + 2].toInt() and 0xff shl 8) or
                    (padded[b + 3].toInt() and 0xff)
            }
            for (i in 16 until 80) {
                w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            }

            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]; var e = h[4]

            for (i in 0 until 80) {
                val (f, kc) = when {
                    i < 20 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                    i < 40 -> (b xor c xor d) to 0x6ed9eba1
                    i < 60 -> ((b and c) or (b and d) or (c and d)) to -0x70e44324
                    else -> (b xor c xor d) to -0x359d3e2a
                }
                val temp = a.rotateLeft(5) + f + e + kc + w[i]
                e = d; d = c; c = b.rotateLeft(30); b = a; a = temp
            }

            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e
            offset += 64
        }

        val out = ByteArray(20)
        for (i in h.indices) {
            out[i * 4] = (h[i] ushr 24).toByte()
            out[i * 4 + 1] = (h[i] ushr 16).toByte()
            out[i * 4 + 2] = (h[i] ushr 8).toByte()
            out[i * 4 + 3] = h[i].toByte()
        }
        return out
    }
}

private fun padMessage(message: ByteArray, bigEndianLength: Boolean): ByteArray {
    val bitLength = message.size.toLong() * 8
    val paddingLength = ((56 - (message.size + 1) % 64) + 64) % 64
    val padded = ByteArray(message.size + 1 + paddingLength + 8)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    val lengthOffset = padded.size - 8
    for (i in 0 until 8) {
        val shift = if (bigEndianLength) (7 - i) * 8 else i * 8
        padded[lengthOffset + i] = (bitLength ushr shift).toByte()
    }
    return padded
}

fun ByteArray.toHex(): String = joinToString("") { byte ->
    val v = byte.toInt() and 0xff
    val hex = v.toString(16)
    if (hex.length == 1) "0$hex" else hex
}

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have an even length" }
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
