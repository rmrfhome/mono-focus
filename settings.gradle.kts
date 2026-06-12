pluginManagement {
    val androidGradlePluginVersion = "8.7.3"
    val kotlinPluginVersion = "2.0.21"

    plugins {
        id("com.android.application") version androidGradlePluginVersion
        id("org.jetbrains.kotlin.android") version kotlinPluginVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinPluginVersion
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MonoFocus"
include(":app")
