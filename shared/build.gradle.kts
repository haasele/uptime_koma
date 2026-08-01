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
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Desktop and Android share the JVM networking stack (CIO engine, InetAddress, JSSE).
    // Wire this with explicit dependsOn: the hierarchy-template android matcher is unreliable
    // with the AGP 9 `android {}` target.
    sourceSets {
        val jvmCommonMain by creating {
            dependsOn(getByName("commonMain"))
        }
        getByName("jvmMain").dependsOn(jvmCommonMain)
        getByName("androidMain").dependsOn(jvmCommonMain)

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

        jvmCommonMain.dependencies {
            implementation(libs.ktor.client.cio)
        }

        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }

        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
        }

        if (iosEnabled) {
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
                implementation(libs.sqldelight.driver.native)
            }
        }
    }
}

sqldelight {
    databases {
        create("KomaDatabase") {
            packageName.set("dev.haasele.koma.shared.db")
            generateAsync.set(false)
        }
    }
}
