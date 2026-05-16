import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.tidal.wear.core.auth"
    compileSdk = 35

    defaultConfig {
        minSdk = 33
        val tidalProperties = Properties().apply {
            val file = rootProject.layout.projectDirectory.file(".dev-secrets\\tidal-app.properties").asFile
            if (file.isFile) file.inputStream().use(::load)
        }
        buildConfigField("String", "TIDAL_CLIENT_ID", (tidalProperties.getProperty("tidal.clientid") ?: "").kotlinLiteral())
        buildConfigField("String", "TIDAL_CLIENT_SECRET", (tidalProperties.getProperty("tidal.clientsecret") ?: "").kotlinLiteral())
        buildConfigField("String", "TIDAL_REDIRECT_URI", (tidalProperties.getProperty("tidal.redirecturi") ?: "").kotlinLiteral())
        val defaultTidalScopes = "user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback"
        buildConfigField("String", "TIDAL_SCOPES", (tidalProperties.getProperty("tidal.scopes") ?: defaultTidalScopes).kotlinLiteral())
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tidal.sdk:auth:0.11.2")
}

fun String.kotlinLiteral(): String = buildString {
    append('"')
    this@kotlinLiteral.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
