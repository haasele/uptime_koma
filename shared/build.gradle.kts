import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.sqldelight)
}

val iosEnabled: Boolean = run {
    val prop = providers.gradleProperty("koma.enableIos").orNull
    if (prop != null && prop != "auto") prop.toBoolean()
    else System.getProperty("os.name").startsWith("Mac")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    android {
        namespace = "dev.haasele.koma.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    if (iosEnabled) {
        // Compose MP 1.11+ dropped iosX64 (Apple x86_64); keep arm64 device + simulator only.
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Amper / Kotlin Toolchain layout
        commonMain {
            kotlin.setSrcDirs(listOf("src"))
            resources.setSrcDirs(emptyList<String>())
        }
        commonTest {
            kotlin.setSrcDirs(listOf("test"))
        }

        // Hierarchy template is off (custom jvmAndAndroidMain); wire Apple targets by hand.
        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
            kotlin.setSrcDirs(listOf("src@jvmAndAndroid"))
        }
        jvmMain {
            dependsOn(jvmAndAndroidMain)
            kotlin.setSrcDirs(listOf("src@jvm"))
        }
        androidMain {
            dependsOn(jvmAndAndroidMain)
            kotlin.setSrcDirs(listOf("src@android"))
        }
        jvmTest {
            kotlin.setSrcDirs(listOf("test@jvm"))
        }

        if (iosEnabled) {
            val iosMain = create("iosMain") {
                dependsOn(commonMain.get())
                kotlin.setSrcDirs(listOf("src@ios"))
                dependencies {
                    implementation(libs.ktor.client.darwin)
                    implementation(libs.sqldelight.driver.native)
                }
            }
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.websockets)
            implementation(libs.ktor.server.content.negotiation)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        jvmAndAndroidMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
            // HTTPS for --https / embedded TLS (CIO has no sslConnector on JVM).
            implementation(libs.ktor.server.netty)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
        }
    }
}

sqldelight {
    databases {
        create("KomaDatabase") {
            packageName.set("dev.haasele.koma.shared.db")
            srcDirs("sqldelight")
            generateAsync.set(false)
        }
    }
}

// Generated interfaces are committed under src/ for Kotlin Toolchain.
// Keep Gradle codegen as a sync helper; do not compile the build/ copy (duplicates).
afterEvaluate {
    kotlin.sourceSets.configureEach {
        val filtered = kotlin.srcDirs.filterNot { dir ->
            dir.path.contains("generated${File.separator}sqldelight") ||
                dir.path.contains("generated/sqldelight")
        }
        kotlin.setSrcDirs(filtered)
    }
}

tasks.register<Copy>("syncSqlDelightToSrc") {
    group = "build"
    description = "Copy SQLDelight generated Kotlin into shared/src for Toolchain"
    dependsOn("generateCommonMainKomaDatabaseInterface")
    from(layout.buildDirectory.dir("generated/sqldelight/code/KomaDatabase/commonMain"))
    into(layout.projectDirectory.dir("src"))
}

tasks.register("syncSqlDelight") {
    group = "build"
    description = "Regenerate SQLDelight and sync into shared/src"
    dependsOn("syncSqlDelightToSrc")
}
