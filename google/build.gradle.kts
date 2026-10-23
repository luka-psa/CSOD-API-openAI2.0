import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The web build's Google OAuth client id (-Phearth.googleWebClientId or HEARTH_GOOGLE_WEB_CLIENT_ID).
// Android needs none: Google recognises the app by its package name and signing key.
val webClientId = (findProperty("hearth.googleWebClientId") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("HEARTH_GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() }
    ?: ""
val generatedConfig = layout.buildDirectory.dir("generated/google/kotlin")
val generateGoogleConfig by tasks.registering {
    inputs.property("webClientId", webClientId)
    outputs.dir(generatedConfig)
    doLast {
        val out = generatedConfig.get().file("app/hearth/google/GoogleConfig.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package app.hearth.google\n\n" +
                "/** Generated from the build's hearth.googleWebClientId. Empty: no Google sign-in in the browser. */\n" +
                "const val GOOGLE_WEB_CLIENT_ID: String = \"" + webClientId + "\"\n",
        )
    }
}

// Google Drive (appDataFolder backups) and Google Calendar (Hearth's own calendar) over plain REST.
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateGoogleConfig)
        }
        commonMain.dependencies {
            api(project(":core:tools"))
            api(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.datetime)
        }
        androidMain.dependencies {
            implementation(libs.play.services.auth)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }
    }
}

android {
    namespace = "app.hearth.google"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
