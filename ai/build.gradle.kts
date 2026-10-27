import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Where Hearth's interpreter model is published (a folder with model.onnx, model.onnx.data and tokenizer.json). Set it with
// -Phearth.interpreterUrl=… or HEARTH_INTERPRETER_URL; empty: the app doesn't offer it.
val interpreterUrl = (findProperty("hearth.interpreterUrl") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("HEARTH_INTERPRETER_URL")?.takeIf { it.isNotBlank() }
    ?: ""
val generatedConfig = layout.buildDirectory.dir("generated/interpreter/kotlin")
val generateInterpreterConfig by tasks.registering {
    inputs.property("interpreterUrl", interpreterUrl)
    outputs.dir(generatedConfig)
    doLast {
        val out = generatedConfig.get().file("app/hearth/ai/interpret/InterpreterConfig.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            "package app.hearth.ai.interpret\n\n" +
                "/** Generated from the build's hearth.interpreterUrl. Empty when this build doesn't offer the interpreter. */\n" +
                "const val INTERPRETER_URL: String = \"" + interpreterUrl.trimEnd('/') + "\"\n",
        )
    }
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateInterpreterConfig)
        }
        commonMain.dependencies {
            api(project(":core:tools"))
            api(libs.serialization.json)
            implementation(libs.ktor.client.core)
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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        jvmTest.dependencies {
            implementation(libs.onnxruntime.jvm)
        }
    }
}

android {
    namespace = "app.hearth.ai"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
