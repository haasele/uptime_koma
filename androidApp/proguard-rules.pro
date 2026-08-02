# Ktor references JVM management APIs that do not exist on Android.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.IntellijIdeaDebugDetector
