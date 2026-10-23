import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The relay's Worker script, embedded so Hearth can install it itself. Single source: relay/src/worker.js.
val relayScript = rootProject.file("relay/src/worker.js")
val generatedRelay = layout.buildDirectory.dir("generated/relay/kotlin")
val generateRelayScript by tasks.registering {
    inputs.file(relayScript)
    outputs.dir(generatedRelay)
    doLast {
        val js = relayScript.readText().replace("$", "${'$'}{'${'$'}'}")
        val out = generatedRelay.get().file("app/hearth/mcp/RelayScript.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package app.hearth.mcp\n\n" +
                "/** Generated from relay/src/worker.js. Do not edit. */\n" +
                "internal const val RELAY_SCRIPT: String = \"\"\"" + js + "\"\"\"\n",
        )
    }
}

// The MCP server (JSON-RPC over the relay's WebSocket) and the relay installer.
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateRelayScript)
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
