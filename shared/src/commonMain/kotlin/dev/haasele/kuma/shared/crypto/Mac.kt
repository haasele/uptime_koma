package dev.haasele.koma.shared.crypto

fun hmac(digest: Digest, key: ByteArray, message: ByteArray): ByteArray {
    val blockKey = when {
        key.size > digest.blockSize -> digest.digest(key).copyOf(digest.blockSize)
        else -> key.copyOf(digest.blockSize)
    }

    val innerPad = ByteArray(digest.blockSize) { (blockKey[it].toInt() xor 0x36).toByte() }
    val outerPad = ByteArray(digest.blockSize) { (blockKey[it].toInt() xor 0x5c).toByte() }

    val inner = digest.digest(innerPad + message)
    return digest.digest(outerPad + inner)
}

/** PBKDF2 with HMAC, used for password and API key hashing. */
fun pbkdf2(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int,
    digest: Digest = Sha256,
): ByteArray {
    require(iterations > 0) { "iterations must be positive" }
    val output = ByteArray(keyLength)
    val blocks = (keyLength + digest.digestSize - 1) / digest.digestSize

    for (block in 1..blocks) {
        val blockIndex = byteArrayOf(
            (block ushr 24).toByte(),
            (block ushr 16).toByte(),
            (block ushr 8).toByte(),
            block.toByte(),
        )
        var u = hmac(digest, password, salt + blockIndex)
        val accumulator = u.copyOf()
        for (i in 1 until iterations) {
            u = hmac(digest, password, u)
            for (j in accumulator.indices) {
                accumulator[j] = (accumulator[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        val offset = (block - 1) * digest.digestSize
        val length = minOf(digest.digestSize, keyLength - offset)
        accumulator.copyInto(output, offset, 0, length)
    }
    return output
}

/** Comparison that does not leak how many leading bytes matched. */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
    return result == 0
}
