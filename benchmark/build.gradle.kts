plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tidal.wear.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 33
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The benchmark test APK instruments the app's `benchmark` build type.
    targetProjectPath = ":app"

    buildTypes {
        create("benchmark") {
            // Test APK must be debuggable so the instrumentation can be installed
            // and profiled; the *target app* variant is non-debuggable.
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}

// AGP's `assembleAndroidTest` is an empty aggregate for a test-only module
// (the module itself *is* the test APK). Point it at the real artifact so
// `./gradlew :benchmark:assembleAndroidTest` performs the compile check.
tasks.matching { it.name == "assembleAndroidTest" }.configureEach {
    dependsOn("assembleBenchmark")
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
