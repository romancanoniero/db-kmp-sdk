plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * db-shadow — capa de migración segura (swap + shadow + comparación de paridad).
 *
 * NO contiene código de plataforma (sin expect/actual): es lógica pura sobre
 * [com.iyr.db.shadow.RtdbBackend], reutilizable en Android/iOS/JS/JVM. Permite a
 * un cliente (IANKMP, Operators, CRM) leer/escribir contra Firebase (primario) y,
 * en paralelo y sin afectar al caller, espejar a db-kmp-sdk (secundario) y
 * comparar snapshots canónicos para detectar regresiones ANTES de migrar.
 */
kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
        publishLibraryVariants("release")
    }

    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    js(IR) {
        browser()
        nodejs()
        binaries.library()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":db-database"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.iyr.db.shadow"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
