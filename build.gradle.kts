plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "io.github.umutcansu.GradleArtisan"
version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        androidStudio("2025.2.1.6")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)


        bundledPlugin("org.jetbrains.android")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
        }

        changeNotes = """
            Summary:
            Fixed deprecated API usage and resolved version compatibility issues across IntelliJ and Android Studio.

        """.trimIndent()
    }
    publishing {
        token.set(providers.gradleProperty("ORG_JETBRAINS_INTELLIJ_PLATFORM_PUBLISH_TOKEN"))
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
