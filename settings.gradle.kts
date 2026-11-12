pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle provision the JDK 21 test runtime when it is not already
    // installed locally (com.tidal.sdk is published as Java 21 bytecode).
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "TidalWearOS"
include(":app")
include(":benchmark")
include(":core:model")
include(":core:auth")
include(":core:tidal-api")
include(":core:playback")
