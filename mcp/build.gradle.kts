import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The address of the one shared relay (relay/, deployed once by the maintainer). Set it with
// -Phearth.relayUrl=… or HEARTH_RELAY_URL; families can still type another one under "Advanced".
val relayUrl = (findProperty("hearth.relayUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("HEARTH_RELAY_URL")?.takeIf { it.isNotBlank() }
    ?: ""
val generatedConfig = layout.buildDirectory.dir("generated/relay/kotlin")
val generateRelayConfig by tasks.registering {
    inputs.property("relayUrl", relayUrl)
    outputs.dir(generatedConfig)
    doLast {
        val out = generatedConfig.get().file("app/hearth/mcp/RelayConfig.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package app.hearth.mcp\n\n" +
                "/** Generated from the build's hearth.relayUrl. Empty when this build has no shared relay. */\n" +
                "const val DEFAULT_RELAY_URL: String = \"" + relayUrl.trimEnd('/') + "\"\n",
        )
    }
}

// The MCP server and the tablet's side of the relay (one WebSocket out).
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateRelayConfig)
        }
        commonMain.dependencies {
            api(project(":core:tools"))
            api(libs.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.coroutines.test)
        }
    }
}

android {
    namespace = "app.hearth.mcp"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
