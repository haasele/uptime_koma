package dev.haasele.koma.shared.notify

import dev.haasele.koma.shared.core.formatDateTime
import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.net.TcpConnection
import dev.haasele.koma.shared.net.openTcp
import dev.haasele.koma.shared.crypto.base64

enum class SmtpSecurity(val id: String) {
    NONE("none"),
    STARTTLS("starttls"),
    IMPLICIT_TLS("tls");

    companion object {
        fun fromId(id: String?): SmtpSecurity = entries.firstOrNull { it.id == id } ?: STARTTLS
    }
}

/**
 * A small SMTP client built on raw sockets. Speaking the protocol directly keeps email working on
 * Android and iOS, where the JavaMail stack is not available.
 */
object SmtpClient {

    suspend fun send(
        host: String,
        port: Int,
        security: SmtpSecurity,
        username: String?,
        password: String?,
        from: String,
        recipients: List<String>,
        subject: String,
        body: String,
        timeoutMs: Long = 20_000,
    ) {
        require(recipients.isNotEmpty()) { "No recipients configured" }

        var connection = openTcp(host, port, timeoutMs, useTls = security == SmtpSecurity.IMPLICIT_TLS)
        try {
            connection.readReply().requirePrefix("2")
            connection.command("EHLO koma-native").requirePrefix("2")

            if (security == SmtpSecurity.STARTTLS) {
                connection.command("STARTTLS").requirePrefix("2")
                connection.close()
                connection = openTcp(host, port, timeoutMs, useTls = true)
                connection.command("EHLO koma-native").requirePrefix("2")
            }

            if (!username.isNullOrBlank() && password != null) {
                connection.command("AUTH LOGIN").requirePrefix("3")
                connection.command(username.encodeToByteArray().base64()).requirePrefix("3")
                connection.command(password.encodeToByteArray().base64()).requirePrefix("2")
            }

            connection.command("MAIL FROM:<$from>").requirePrefix("2")
            recipients.forEach { recipient -> connection.command("RCPT TO:<$recipient>").requirePrefix("2") }
            connection.command("DATA").requirePrefix("3")

            connection.send((buildMessage(from, recipients, subject, body) + "\r\n.\r\n").encodeToByteArray())
            connection.readReply().requirePrefix("2")
            connection.command("QUIT")
        } finally {
            connection.close()
        }
    }

    private fun buildMessage(from: String, recipients: List<String>, subject: String, body: String): String =
        buildString {
            append("From: ").append(from).append("\r\n")
            append("To: ").append(recipients.joinToString(", ")).append("\r\n")
            append("Subject: ").append(subject).append("\r\n")
            append("Date: ").append(nowMs().formatDateTime()).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
            // A line starting with a dot would terminate the DATA block early.
            append(body.replace("\r\n", "\n").lines().joinToString("\r\n") { if (it.startsWith(".")) ".$it" else it })
        }

    private suspend fun TcpConnection.command(line: String): String {
        send((line + "\r\n").encodeToByteArray())
        return readReply()
    }

    /** EHLO and other verbs answer with several `250-` continuation lines before the final one. */
    private suspend fun TcpConnection.readReply(): String {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readLine()
            lines += line
            if (line.length < 4 || line[3] != '-') break
        }
        return lines.joinToString("\n")
    }

    private fun String.requirePrefix(prefix: String) {
        if (!startsWith(prefix)) throw NotificationException("SMTP server replied: ${take(160)}")
    }
}
