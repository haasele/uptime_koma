package dev.haasele.koma.shared.crypto

import kotlin.io.encoding.Base64

fun ByteArray.base64(): String = Base64.Default.encode(this)

fun String.base64(): String = encodeToByteArray().base64()
