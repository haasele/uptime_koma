package dev.haasele.koma.app.desktop

/**
 * Sets Linux/Wayland AWT env vars before the toolkit starts.
 *
 * AWT reads `_JAVA_AWT_WM_NONREPARENTING` from the process environment (not
 * [System.setProperty]). Packaged launchers set this in the shell; this object
 * is the in-process fallback for bare `java -jar` / IDE runs.
 *
 * @see <a href="https://github.com/niri-wm/niri/wiki/Application-Issues">niri Application Issues</a>
 */
internal object WaylandAwtBootstrap {
    fun apply() {
        val isLinux = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)
        if (!isLinux) return

        putEnv("_JAVA_AWT_WM_NONREPARENTING", "1")
        if (System.getenv("GDK_SCALE").isNullOrBlank()) putEnv("GDK_SCALE", "1")
        if (System.getenv("GDK_DPI_SCALE").isNullOrBlank()) putEnv("GDK_DPI_SCALE", "1")

        val onWayland = !System.getenv("WAYLAND_DISPLAY").isNullOrBlank() ||
            System.getenv("XDG_SESSION_TYPE").equals("wayland", ignoreCase = true)
        if (onWayland) {
            System.err.println(
                "koma-desktop: Wayland — _JAVA_AWT_WM_NONREPARENTING=1 " +
                    "(niri/xwayland-satellite Java AWT)",
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun putEnv(key: String, value: String) {
        if (System.getenv(key) == value) return
        try {
            val processEnvironment = Class.forName("java.lang.ProcessEnvironment")
            val theEnvironment = processEnvironment.getDeclaredField("theEnvironment").apply {
                isAccessible = true
            }
            (theEnvironment.get(null) as MutableMap<String, String>)[key] = value
            runCatching {
                val caseInsensitive = processEnvironment.getDeclaredField("theCaseInsensitiveEnvironment")
                caseInsensitive.isAccessible = true
                (caseInsensitive.get(null) as? MutableMap<String, String>)?.put(key, value)
            }
            return
        } catch (_: Throwable) {
            // fall through
        }
        try {
            val env = System.getenv()
            val mapField = env.javaClass.getDeclaredField("m")
            mapField.isAccessible = true
            (mapField.get(env) as MutableMap<String, String>)[key] = value
        } catch (t: Throwable) {
            System.err.println(
                "koma-desktop: could not set $key=$value in-process (${t.message}). " +
                    "Use a packaged launcher, or export it in niri " +
                    "environment { _JAVA_AWT_WM_NONREPARENTING \"1\" }.",
            )
        }
    }
}
