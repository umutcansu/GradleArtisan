plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.7.1"
}

group = "io.github.umutcansu.GradleArtisan"
version = "1.0.1"

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
            Refactored document change handling to remove deprecated and experimental API usage.
            Icon changed

            Details:

            Replaced deprecated project.baseDir access with project.basePath and VirtualFileManager API.

            Removed usage of experimental WriteIntentReadAction.

            Introduced stable WriteCommandAction.runWriteCommandAction(project) for editor modifications.

            Moved UI update calls to ApplicationManager.getApplication().invokeLater for proper EDT handling.

            Ensured all document and editor model access occurs within appropriate read/write actions for thread safety.
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
