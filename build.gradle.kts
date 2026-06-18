/**
 * db-kmp-sdk
 *
 * Reemplazo API-compatible de firebase-kmp-sdk que, en lugar de envolver los
 * SDK nativos de Firebase, habla un protocolo WebSocket propio contra un motor
 * relacional (PostgreSQL) alojado en un VPS (db-realtime-gateway).
 *
 * A diferencia de firebase-kmp-sdk, el ~90% del código vive en commonMain
 * (cliente Ktor WebSocket), por lo que NO requiere CocoaPods ni dependencias
 * nativas de Firebase. expect/actual queda reducido al engine de Ktor.
 *
 * Targets: androidTarget, iosX64/iosArm64/iosSimulatorArm64, js(IR).
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "io.github.romancanoniero"
    version = "0.1.0-SNAPSHOT"
}
