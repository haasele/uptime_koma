package dev.haasele.koma.app.desktop

/**
 * Shared CLI for every desktop package (JAR, jpackage binary, AppImage, Flatpak).
 *
 * ```
 * koma-native --nogui --port 3001
 * koma-native --nogui --https /path/to/keystore.p12 --port 8443
 * koma-native --debug
 * ```
 */
data class DesktopCli(
    val nogui: Boolean = false,
    val port: Int? = null,
    val http: Boolean = false,
    val httpsCertPath: String? = null,
    /** Public DNS names for --http/--https (Host allowlist + log URLs). */
    val hostnames: List<String> = emptyList(),
    val debug: Boolean = false,
    val help: Boolean = false,
    val errors: List<String> = emptyList(),
) {
    /** True when the process should run without Compose / AWT. */
    val isHeadless: Boolean get() = nogui || port != null

    /** Prefer TLS when a certificate path was given; otherwise plain HTTP. */
    val useHttps: Boolean get() = !httpsCertPath.isNullOrBlank()

    /** True when CLI owns the listen socket (--http / --https / --port / --nogui). */
    val managesEmbeddedServer: Boolean get() = isHeadless || http || useHttps

    val ok: Boolean get() = errors.isEmpty()

    companion object {
        fun parse(args: Array<String>): DesktopCli {
            var nogui = false
            var port: Int? = null
            var http = false
            var httpsCertPath: String? = null
            var hostnames = emptyList<String>()
            var debug = false
            var help = false
            val errors = mutableListOf<String>()

            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "-h", "--help" -> help = true
                    "--debug" -> debug = true
                    "--nogui", "--headless" -> nogui = true
                    "--http" -> http = true
                    "--https" -> {
                        val path = args.getOrNull(i + 1)
                        if (path.isNullOrBlank() || path.startsWith("-")) {
                            errors += "--https requires a certificate path (PKCS12 .p12/.pfx or PEM)"
                        } else {
                            httpsCertPath = path
                            i++
                        }
                    }
                    "--hostname", "--hostnames" -> {
                        val value = args.getOrNull(i + 1)
                        if (value.isNullOrBlank() || value.startsWith("-")) {
                            errors += "--hostname requires a comma-separated list of DNS names"
                        } else {
                            hostnames = parseHostnameList(value)
                            if (hostnames.isEmpty()) {
                                errors += "--hostname list is empty"
                            }
                            i++
                        }
                    }
                    "--port" -> {
                        val value = args.getOrNull(i + 1)
                        val parsed = value?.toIntOrNull()
                        when {
                            value.isNullOrBlank() || value.startsWith("-") ->
                                errors += "--port requires a number"
                            parsed == null || parsed !in 1..65535 ->
                                errors += "--port must be an integer between 1 and 65535"
                            else -> {
                                port = parsed
                                i++
                            }
                        }
                    }
                    else -> {
                        when {
                            arg.startsWith("--https=") -> {
                                httpsCertPath = arg.removePrefix("--https=").ifBlank {
                                    errors += "--https= requires a certificate path"
                                    null
                                }
                            }
                            arg.startsWith("--hostname=") || arg.startsWith("--hostnames=") -> {
                                val raw = arg.substringAfter('=')
                                hostnames = parseHostnameList(raw)
                                if (hostnames.isEmpty()) {
                                    errors += "--hostname= requires a comma-separated list of DNS names"
                                }
                            }
                            arg.startsWith("--port=") -> {
                                val parsed = arg.removePrefix("--port=").toIntOrNull()
                                if (parsed == null || parsed !in 1..65535) {
                                    errors += "--port must be an integer between 1 and 65535"
                                } else {
                                    port = parsed
                                }
                            }
                            arg.startsWith("-") -> errors += "Unknown option: $arg"
                            else -> errors += "Unexpected argument: $arg"
                        }
                    }
                }
                i++
            }

            if (http && !httpsCertPath.isNullOrBlank()) {
                errors += "Use either --http or --https, not both"
            }
            if (hostnames.isNotEmpty() && !http && httpsCertPath.isNullOrBlank() && !nogui && port == null) {
                errors += "--hostname requires --http, --https, --nogui or --port"
            }
            if (nogui && http.not() && httpsCertPath.isNullOrBlank()) {
                // Default to HTTP for headless webservice.
                http = true
            }
            // `--port` alone means “web service only”.
            if (port != null) {
                nogui = true
                if (!http && httpsCertPath.isNullOrBlank()) http = true
            }

            return DesktopCli(
                nogui = nogui,
                port = port,
                http = http,
                httpsCertPath = httpsCertPath,
                hostnames = hostnames,
                debug = debug,
                help = help,
                errors = errors,
            )
        }

        fun parseHostnameList(raw: String): List<String> =
            raw.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()

        fun helpText(commandName: String = "koma-native"): String = """
            |Uptime Koma desktop
            |
            |Usage:
            |  $commandName [options]
            |
            |Options:
            |  --nogui, --headless     Run without a GUI (engine + embedded HTTP API)
            |  --port <n>              Listen port; implies --nogui (web service only)
            |  --http                  Plain HTTP (default with --nogui / --port)
            |  --https <cert>          HTTPS using a PKCS12 (.p12/.pfx) keystore or PEM cert
            |                          (private key: sibling key.pem / <name>.key, or same PEM)
            |                          Password: env KOMA_HTTPS_PASSWORD (default: changeit for PKCS12)
            |  --hostname <list>       Comma-separated public DNS names for --http/--https
            |                          (Host allowlist + advertised URLs), e.g.
            |                          monitoring.com,monitoring.example.com,srv-monitor01.domain.internal
            |  --debug                 Verbose logs (slf4j DEBUG + Koma engine events)
            |  -h, --help              Show this help
            |
            |Examples:
            |  $commandName
            |  $commandName --nogui --port 3001
            |  $commandName --port 8443 --https /etc/koma/keystore.p12
            |  $commandName --nogui --http --port 3001 --hostname monitoring.example.com,srv-monitor01.lan
            |  $commandName --nogui --http --port 3001 --debug
            |
            |Packages (JAR / binary / AppImage / Flatpak) all accept the same flags.
            |Flatpak: flatpak run --filesystem=host:ro dev.haasele.KomaNative -- --https /path/to/cert ...
            """.trimMargin()
    }
}

/** Apply --debug before any logger is initialized. */
fun applyDebugLogging(enabled: Boolean) {
    if (!enabled) return
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")
    System.setProperty("org.slf4j.simpleLogger.showDateTime", "true")
    System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS")
    System.setProperty("koma.debug", "true")
}

fun isKomaDebugEnabled(): Boolean =
    System.getProperty("koma.debug") == "true"
