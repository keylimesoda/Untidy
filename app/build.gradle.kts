import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.tidal.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tidal.wear"
        minSdk = 33
        targetSdk = 35
        versionCode = 13
        versionName = "0.6.0-beta.8"

        val tidalProperties = Properties().apply {
            // Cross-platform path via java.io.File resolution (was a Windows
            // backslash literal, which silently never resolves on Linux/macOS).
            val file = rootProject.file(".dev-secrets/tidal-app.properties")
            if (file.isFile) {
                file.inputStream().use(::load)
            } else {
                logger.warn(
                    "WARNING [tidal-secrets]: secrets file not found at ${file.absolutePath}. " +
                        "TIDAL_CLIENT_ID/TIDAL_CLIENT_SECRET/TIDAL_REDIRECT_URI will compile as EMPTY strings and sign-in will fail. " +
                        "Create .dev-secrets/tidal-app.properties with tidal.clientid=..., tidal.clientsecret=..., tidal.redirecturi=...",
                )
            }
        }
        buildConfigField("String", "TIDAL_CLIENT_ID", (tidalProperties.getProperty("tidal.clientid") ?: "").kotlinLiteral())
        buildConfigField("String", "TIDAL_CLIENT_SECRET", (tidalProperties.getProperty("tidal.clientsecret") ?: "").kotlinLiteral())
        buildConfigField("String", "TIDAL_REDIRECT_URI", (tidalProperties.getProperty("tidal.redirecturi") ?: "").kotlinLiteral())
        val defaultTidalScopes = "user.read collection.read collection.write playlists.read playlists.write search.read search.write recommendations.read entitlements.read playback"
        buildConfigField("String", "TIDAL_SCOPES", (tidalProperties.getProperty("tidal.scopes") ?: defaultTidalScopes).kotlinLiteral())
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local installable release; replace with Play Store signing for distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("benchmark") {
            // Release-like, non-debuggable target for Macrobenchmark. No baseline
            // profile is generated or consumed. Keep minification off so the
            // benchmark test APK does not need matching shrinker configuration.
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Roborazzi/Robolectric needs the merged resources and manifest.
            isIncludeAndroidResources = true
        }
    }
}

roborazzi {
    // Golden PNGs are committed next to the JVM tests so review diffs are visible
    // in the same change as the screens they cover.
    outputDir.set(file("src/test/snapshots"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:auth"))
    implementation(project(":core:tidal-api"))
    implementation(project(":core:playback"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tidal.sdk:auth:0.11.2")
    implementation("com.tidal.sdk:player:0.0.64")
    implementation("com.tidal.sdk:eventproducer:0.3.2")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-material:1.5.0")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.wear.compose:compose-navigation:1.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-input:1.2.0")
    implementation("androidx.wear:wear-ongoing:1.0.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.tidal.androidx.media3:media3-session:1.5.0.1")
    debugImplementation("com.tidal.androidx.media3:media3-datasource:1.5.0.1")
    debugImplementation("com.tidal.androidx.media3:media3-exoplayer:1.5.0.1")
    debugImplementation("com.tidal.androidx.media3:media3-exoplayer-dash:1.5.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.horologist:horologist-compose-layout:0.6.22")
    implementation("com.google.android.horologist:horologist-compose-material:0.6.22")
    implementation("com.google.android.horologist:horologist-media-ui:0.6.22")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.5")
    testImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.46.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.46.1")
    // Provides the ComponentActivity used by createAndroidComposeRule in local tests.
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.5")
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


