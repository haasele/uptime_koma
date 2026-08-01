package dev.haasele.koma.shared.engine.executors

import dev.haasele.koma.shared.core.nowMs
import dev.haasele.koma.shared.domain.CheckResult
import dev.haasele.koma.shared.domain.ConditionEvaluator
import dev.haasele.koma.shared.domain.HttpAuthMethod
import dev.haasele.koma.shared.domain.Monitor
import dev.haasele.koma.shared.domain.MonitorStatus
import dev.haasele.koma.shared.domain.MonitorType
import dev.haasele.koma.shared.domain.BodyEncoding
import dev.haasele.koma.shared.domain.matchesStatusCode
import dev.haasele.koma.shared.engine.CheckContext
import dev.haasele.koma.shared.engine.CheckExecutor
import dev.haasele.koma.shared.json.JsonQuery
import dev.haasele.koma.shared.net.HttpClientSpec
import dev.haasele.koma.shared.net.TlsInspector
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import dev.haasele.koma.shared.crypto.base64

class HttpExecutor(override val type: MonitorType) : CheckExecutor {

    override suspend fun check(monitor: Monitor, context: CheckContext): CheckResult {
        val config = monitor.config
        val proxy = context.proxyProvider(monitor.proxyId)
        val client = context.httpClients.get(
            HttpClientSpec(
                ignoreTls = config.ignoreTls,
                followRedirects = config.maxRedirects > 0,
                maxRedirects = config.maxRedirects,
                timeoutMs = monitor.timeoutSeconds * 1000L,
                proxy = proxy,
            ),
        )

        val start = nowMs()
        val response: HttpResponse = client.request(config.url) {
            method = KtorHttpMethod.parse(config.method.name)
            headers {
                config.headers.forEach { (key, value) -> append(key, value) }
                when (config.authMethod) {
                    HttpAuthMethod.BASIC -> append(
                        "Authorization",
                        "Basic " + "${config.basicAuthUser}:${config.basicAuthPassword}".base64(),
                    )
                    HttpAuthMethod.BEARER -> append("Authorization", "Bearer ${config.bearerToken}")
                    HttpAuthMethod.NTLM, HttpAuthMethod.NONE -> Unit
                }
            }
            if (config.body.isNotBlank()) {
                contentType(
                    when (config.bodyEncoding) {
                        BodyEncoding.JSON -> ContentType.Application.Json
                        BodyEncoding.FORM -> ContentType.Application.FormUrlEncoded
                        BodyEncoding.XML -> ContentType.Application.Xml
                        BodyEncoding.TEXT -> ContentType.Text.Plain
                    },
                )
                setBody(config.body)
            }
        }

        val elapsed = nowMs() - start
        val statusCode = response.status.value
        val body = runCatching { response.bodyAsText() }.getOrDefault("")

        val certificate = inspectCertificateIfNeeded(monitor)
        val variables = mutableMapOf(
            "response_code" to listOf(statusCode.toString()),
            "response_time" to listOf(elapsed.toString()),
            "response_body" to listOf(body),
        )

        if (!config.acceptedStatusCodes.matchesStatusCode(statusCode)) {
            return CheckResult(
                status = MonitorStatus.DOWN,
                message = "$statusCode - ${response.status.description}",
                pingMs = elapsed,
                certificate = certificate,
                variables = variables,
            )
        }

        val typeResult = when (type) {
            MonitorType.KEYWORD -> evaluateKeyword(monitor, body, elapsed, certificate, variables)
            MonitorType.JSON_QUERY -> evaluateJsonQuery(monitor, body, elapsed, certificate, variables)
            else -> CheckResult(
                status = MonitorStatus.UP,
                message = "$statusCode - ${response.status.description}",
                pingMs = elapsed,
                certificate = certificate,
                variables = variables,
            )
        }

        if (typeResult.status != MonitorStatus.UP) return typeResult

        if (monitor.config.conditions.isNotEmpty() &&
            !ConditionEvaluator.evaluate(monitor.config.conditions, typeResult.variables)
        ) {
            return typeResult.copy(status = MonitorStatus.DOWN, message = "Conditions not met")
        }
        return typeResult
    }

    private fun evaluateKeyword(
        monitor: Monitor,
        body: String,
        elapsed: Long,
        certificate: dev.haasele.koma.shared.domain.CertificateInfo?,
        variables: MutableMap<String, List<String>>,
    ): CheckResult {
        val found = body.contains(monitor.config.keyword, ignoreCase = true)
        variables["keyword_found"] = listOf(found.toString())
        val success = if (monitor.config.invertKeyword) !found else found
        val description = if (monitor.config.invertKeyword) "must not appear" else "not found"
        return if (success) {
            CheckResult(MonitorStatus.UP, "Keyword check passed", elapsed, certificate, variables)
        } else {
            CheckResult(
                MonitorStatus.DOWN,
                "Keyword \"${monitor.config.keyword}\" $description in response",
                elapsed,
                certificate,
                variables,
            )
        }
    }

    private fun evaluateJsonQuery(
        monitor: Monitor,
        body: String,
        elapsed: Long,
        certificate: dev.haasele.koma.shared.domain.CertificateInfo?,
        variables: MutableMap<String, List<String>>,
    ): CheckResult {
        val extracted = runCatching { JsonQuery.evaluate(body, monitor.config.jsonPath) }
            .getOrElse {
                return CheckResult(
                    MonitorStatus.DOWN,
                    "JSON query failed: ${it.message}",
                    elapsed,
                    certificate,
                    variables,
                )
            }
        variables["json_query_result"] = extracted
        val expected = monitor.config.expectedValue
        val matches = expected.isBlank() || extracted.any { it == expected }
        return if (matches) {
            CheckResult(MonitorStatus.UP, "JSON query matched", elapsed, certificate, variables)
        } else {
            CheckResult(
                MonitorStatus.DOWN,
                "Expected \"$expected\" but got \"${extracted.joinToString()}\"",
                elapsed,
                certificate,
                variables,
            )
        }
    }

    private suspend fun inspectCertificateIfNeeded(monitor: Monitor): dev.haasele.koma.shared.domain.CertificateInfo? {
        if (!monitor.config.certificateExpiryNotification) return null
        val url = runCatching { Url(monitor.config.url) }.getOrNull() ?: return null
        if (url.protocol != URLProtocol.HTTPS) return null
        return TlsInspector.inspect(url.host, url.port, monitor.timeoutSeconds * 1000L)
    }
}
