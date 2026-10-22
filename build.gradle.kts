plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}

// One command for everything a change must pass: shared tests on JVM + WebAssembly,
// the Android app (build + lint) and the web bundle.
tasks.register("hearthCheck") {
    group = "verification"
    dependsOn(
        ":core:data:jvmTest", ":core:tools:jvmTest", ":ai:jvmTest",
        ":core:tools:wasmJsNodeTest", ":ai:wasmJsNodeTest",
        ":app:assembleDebug", ":app:lintDebug",
        ":web:wasmJsBrowserDistribution",
    )
}
