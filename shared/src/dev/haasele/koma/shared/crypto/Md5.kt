package dev.haasele.koma.shared.crypto

/**
 * MD5 is only here because RADIUS mandates it for the request authenticator; it is deliberately
 * not exposed to any password or token path.
 */
object Md5 : Digest {
    override val blockSize: Int = 64
    override val digestSize: Int = 16

    private val shifts = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    /** The RFC sine table, written out so no floating point maths is involved on any target. */
    private val sines = intArrayOf(
        -0x28955b88, -0x173848aa, 0x242070db, -0x3e423112,
        -0x0a83f051, 0x4787c62a, -0x57cfb9ed, -0x02b96aff,
        0x698098d8, -0x74bb0851, -0x0000a44f, -0x76a32842,
        0x6b901122, -0x02678e6d, -0x5986bc72, 0x49b40821,
        -0x09e1da9e, -0x3fbf4cc0, 0x265e5a51, -0x16493856,
        -0x29d0efa3, 0x02441453, -0x275e197f, -0x182c0438,
        0x21e1cde6, -0x3cc8f82a, -0x0b2af279, 0x455a14ed,
        -0x561c16fb, -0x03105c08, 0x676f02d9, -0x72d5b376,
        -0x0005c6be, -0x788e097f, 0x6d9d6122, -0x021ac7f4,
        -0x5b4115bc, 0x4bdecfa9, -0x0944b4a0, -0x41404390,
        0x289b7ec6, -0x155ed806, -0x2b10cf7b, 0x04881d05,
        -0x262b2fc7, -0x1924661b, 0x1fa27cf8, -0x3b53a99b,
        -0x0bd6ddbc, 0x432aff97, -0x546bdc59, -0x036c5fc7,
        0x655b59c3, -0x70f3336e, -0x00100b83, -0x7a7ba22f,
        0x6fa87e4f, -0x01d31920, -0x5cfebcec, 0x4e0811a1,
        -0x08ac817e, -0x42c50dcb, 0x2ad7d2bb, -0x14792c6f,
    )

    override fun digest(message: ByteArray): ByteArray {
        var a0 = 0x67452301
        var b0 = -0x10325477
        var c0 = -0x67452302
        var d0 = 0x10325476

        val padded = pad(message)
        val block = IntArray(16)

        var offset = 0
        while (offset < padded.size) {
            for (i in 0 until 16) {
                val b = offset + i * 4
                block[i] = (padded[b].toInt() and 0xff) or
                    ((padded[b + 1].toInt() and 0xff) shl 8) or
                    ((padded[b + 2].toInt() and 0xff) shl 16) or
                    ((padded[b + 3].toInt() and 0xff) shl 24)
            }

            var a = a0
            var b = b0
            var c = c0
            var d = d0

            for (i in 0 until 64) {
                val (f, g) = when {
                    i < 16 -> ((b and c) or (b.inv() and d)) to i
                    i < 32 -> ((d and b) or (d.inv() and c)) to (5 * i + 1) % 16
                    i < 48 -> (b xor c xor d) to (3 * i + 5) % 16
                    else -> (c xor (b or d.inv())) to (7 * i) % 16
                }
                val temp = d
                d = c
                c = b
                b += (a + f + sines[i] + block[g]).rotateLeft(shifts[i])
                a = temp
            }

            a0 += a
            b0 += b
            c0 += c
            d0 += d
            offset += 64
        }

        val out = ByteArray(16)
        listOf(a0, b0, c0, d0).forEachIndexed { index, value ->
            out[index * 4] = value.toByte()
            out[index * 4 + 1] = (value ushr 8).toByte()
            out[index * 4 + 2] = (value ushr 16).toByte()
            out[index * 4 + 3] = (value ushr 24).toByte()
        }
        return out
    }

    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddingLength = ((56 - (message.size + 1) % 64) + 64) % 64
        val padded = ByteArray(message.size + 1 + paddingLength + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        val lengthOffset = padded.size - 8
        for (i in 0 until 8) {
            padded[lengthOffset + i] = (bitLength ushr (i * 8)).toByte()
        }
        return padded
    }
}
