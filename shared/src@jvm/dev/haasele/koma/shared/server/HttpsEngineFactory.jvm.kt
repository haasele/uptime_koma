package dev.haasele.koma.shared.server

import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer as KtorEmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

internal actual object HttpsEngineFactory {
    actual fun create(
        host: String,
        port: Int,
        certificatePath: String,
        module: Application.() -> Unit,
    ): KtorEmbeddedServer<*, *>? = runCatching {
        val password = httpsPassword()
        val keyStore = loadKeyStore(File(certificatePath), password)
        val alias = keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }
            ?: error("No private-key entry in $certificatePath")

        embeddedServer(
            factory = Netty,
            configure = {
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = alias,
                    keyStorePassword = { password.copyOf() },
                    privateKeyPassword = { password.copyOf() },
                ) {
                    this.host = host
                    this.port = port
                }
            },
            module = module,
        )
    }.onFailure { error ->
        System.err.println("koma: HTTPS engine failed: ${error.message}")
    }.getOrNull()

    private fun httpsPassword(): CharArray {
        val fromEnv = System.getenv("KOMA_HTTPS_PASSWORD")
        return (fromEnv ?: "changeit").toCharArray()
    }

    private fun loadKeyStore(path: File, password: CharArray): KeyStore {
        if (!path.exists()) error("Certificate path not found: ${path.absolutePath}")

        if (path.isDirectory) {
            val p12 = listOf("keystore.p12", "keystore.pfx", "cert.p12", "cert.pfx")
                .map { path.resolve(it) }
                .firstOrNull { it.isFile }
            if (p12 != null) return loadPkcs12(p12, password)
            val cert = listOf("cert.pem", "fullchain.pem", "certificate.pem", "cert.crt")
                .map { path.resolve(it) }
                .firstOrNull { it.isFile }
                ?: error("No keystore.p12 / cert.pem in ${path.absolutePath}")
            val key = listOf("key.pem", "privkey.pem", "private.pem", "cert.key")
                .map { path.resolve(it) }
                .firstOrNull { it.isFile }
                ?: error("No key.pem next to ${cert.name} in ${path.absolutePath}")
            return loadPem(cert, key, password)
        }

        val name = path.name.lowercase()
        return when {
            name.endsWith(".p12") || name.endsWith(".pfx") -> loadPkcs12(path, password)
            name.endsWith(".pem") || name.endsWith(".crt") || name.endsWith(".cer") -> {
                val key = findSiblingKey(path)
                    ?: error("PEM certificate needs a private key (sibling key.pem / ${path.nameWithoutExtension}.key)")
                loadPem(path, key, password)
            }
            else -> loadPkcs12(path, password)
        }
    }

    private fun findSiblingKey(cert: File): File? {
        val parent = cert.parentFile ?: return null
        val stem = cert.nameWithoutExtension
        return listOf(
            parent.resolve("key.pem"),
            parent.resolve("privkey.pem"),
            parent.resolve("private.pem"),
            parent.resolve("$stem.key"),
            parent.resolve("$stem-key.pem"),
            cert, // allow cert+key concatenated in one PEM
        ).firstOrNull { it.isFile }
    }

    private fun loadPkcs12(file: File, password: CharArray): KeyStore {
        val store = KeyStore.getInstance("PKCS12")
        FileInputStream(file).use { store.load(it, password) }
        return store
    }

    private fun loadPem(certFile: File, keyFile: File, password: CharArray): KeyStore {
        val certificates = FileInputStream(certFile).use { input ->
            CertificateFactory.getInstance("X.509").generateCertificates(input).toList()
        }
        if (certificates.isEmpty()) error("No X.509 certificates in ${certFile.absolutePath}")
        val privateKey = readPrivateKey(keyFile.readText())
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, password)
        store.setKeyEntry("koma", privateKey, password, certificates.toTypedArray())
        return store
    }

    private fun readPrivateKey(pem: String): PrivateKey {
        val pkcs8 = decodePemBlock(pem, "PRIVATE KEY")
            ?: error("No PKCS#8 PRIVATE KEY block in PEM (convert with: openssl pkcs8 -topk8 -nocrypt)")
        val spec = PKCS8EncodedKeySpec(pkcs8)
        val algorithms = listOf("RSA", "EC", "Ed25519", "EdDSA")
        var last: Exception? = null
        for (algorithm in algorithms) {
            try {
                return KeyFactory.getInstance(algorithm).generatePrivate(spec)
            } catch (error: Exception) {
                last = error
            }
        }
        throw IllegalArgumentException("Unsupported private key format", last)
    }

    private fun decodePemBlock(pem: String, label: String): ByteArray? {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val start = pem.indexOf(begin)
        if (start < 0) return null
        val endIdx = pem.indexOf(end, startIndex = start)
        if (endIdx < 0) return null
        val body = pem.substring(start + begin.length, endIdx).replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(body)
    }
}
