import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        namespace = "dev.haasele.koma.app"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources.enable = true
    }

    if (iosEnabled) {
        // Compose MP 1.11+ dropped iosX64 (Apple x86_64); keep arm64 device + simulator only.
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "KomaApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.setSrcDirs(listOf("src"))
        }
        commonTest {
            kotlin.setSrcDirs(listOf("test"))
        }
        jvmMain {
            kotlin.setSrcDirs(listOf("src@jvm"))
        }
        androidMain {
            kotlin.setSrcDirs(listOf("src@android"))
        }
        if (iosEnabled) {
            // Hierarchy template is off project-wide; connect iosMain to Apple compilations.
            val iosMain = create("iosMain") {
                dependsOn(commonMain.get())
                kotlin.setSrcDirs(listOf("src@ios"))
            }
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        }

        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(libs.compose.material.icons.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.appcompat)
        }
    }
}

// No composeResources/ assets and custom Amper source layout; empty Res collectors break
// Apple compilations (ActualResourceCollectors references Res that never lands on the classpath).
compose.resources {
    generateResClass = never
}
