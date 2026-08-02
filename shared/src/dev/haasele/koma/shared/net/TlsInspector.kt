package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.domain.CertificateInfo

/**
 * Reads the leaf certificate of a TLS endpoint. Apple targets do not expose the peer chain
 * through a portable API, so the iOS implementation reports no certificate instead of failing
 * the whole check.
 */
expect object TlsInspector {
    suspend fun inspect(host: String, port: Int, timeoutMs: Long): CertificateInfo?
}
