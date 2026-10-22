import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    // JVM target exists for fast host tests (in-memory bundled SQLite).
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.room3.runtime)
            api(libs.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.sqlite.framework)
        }
        jvmMain.dependencies {
            implementation(libs.sqlite.bundled)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        wasmJsMain.dependencies {
            implementation(libs.sqlite.web)
            implementation(libs.kotlinx.browser)
            // SQLite (WASM build) running in a web worker; see worker/worker.js.
            implementation(npm("hearth-sqlite-worker", layout.projectDirectory.dir("worker").asFile))
        }
    }
}

android {
    namespace = "app.hearth.core.data"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    add("kspAndroid", libs.room3.compiler)
    add("kspJvm", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}
