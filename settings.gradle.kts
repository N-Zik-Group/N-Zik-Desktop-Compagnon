pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // The in-app updater's changelog-translation library (sanctioned by spec `spec-updater`,
        // loop 2 — the phone's `com.github.rebelonion:translator`) is published on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "N-Zik-Desktop-Compagnon"
include(":app")
