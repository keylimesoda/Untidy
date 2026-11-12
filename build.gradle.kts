plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.android.test") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.diffplug.spotless") version "8.11.0" apply false
    id("io.github.takahirom.roborazzi") version "1.46.1" apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        parallel = true
        source.setFrom(
            project.fileTree(project.projectDir) {
                include("src/**/*.kt")
                exclude("**/build/**")
            },
        )
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        // origin/main is not fetched in this checkout, so ratchet against the
        // local main branch. Existing Kotlin is left untouched; new/changed
        // files must satisfy ktlint.
        ratchetFrom("main")
        kotlin {
            target("src/**/*.kt")
            ktlint("1.8.0")
        }
    }

    dependencies {
        add("detektPlugins", "io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("detekt")
        dependsOn("spotlessCheck")
    }
}
