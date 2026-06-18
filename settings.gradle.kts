pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "db-kmp-sdk"

// SDK cliente KMP que reemplaza a firebase-kmp-sdk hablando un protocolo
// WebSocket propio contra db-realtime-gateway (Ktor + PostgreSQL en VPS).
// Mismos targets que firebase-kmp-sdk: android, iOS, JS.
include(":db-core")
include(":db-database")
include(":db-shadow")
