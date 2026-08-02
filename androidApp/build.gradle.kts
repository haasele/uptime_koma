plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "dev.haasele.koma.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.haasele.koma"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Amper / Kotlin Toolchain Android layout
    sourceSets.getByName("main") {
        manifest.srcFile("src/AndroidManifest.xml")
        res.directories.clear()
        res.directories.add("res")
        resources.directories.clear()
        resources.directories.add("resources")
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.appcompat)
}
