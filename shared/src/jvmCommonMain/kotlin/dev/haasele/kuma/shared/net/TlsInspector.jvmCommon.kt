package dev.haasele.koma.shared.net

import dev.haasele.koma.shared.core.ioDispatcher
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CertificateInfo
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

actual object TlsInspector {

    actual suspend fun inspect(host: String, port: Int, timeoutMs: Long): CertificateInfo? =
        withContext(ioDispatcher) {
            runCatching {
                val factory = trustAllSslContext().socketFactory
                Socket().use { plain ->
                    plain.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                    (factory.createSocket(plain, host, port, true) as SSLSocket).use { socket ->
                        socket.soTimeout = timeoutMs.toInt()
                        socket.startHandshake()
                        val certificate = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                            ?: return@use null
                        val validTo = certificate.notAfter.time
                        CertificateInfo(
                            subject = certificate.subjectX500Principal.name,
                            issuer = certificate.issuerX500Principal.name,
                            validToMs = validTo,
                            daysRemaining = ((validTo - nowMs()) / 86_400_000L).toInt(),
                            valid = runCatching { certificate.checkValidity(); true }.getOrDefault(false),
                        )
                    }
                }
            }.getOrNull()
        }
}
