plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.hearth"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.hearth"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        versionName = "0.24.0"
        // Tablets and phones are ARM: ONNX Runtime's x86 libraries would only double the download (and push the APK
        // past GitHub's 100 MB limit for the download site).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // One fixed key for every build, so a new APK installs over the old one and the family's data stays.
    // It only identifies "this is Hearth"; it is not a secret that protects the household.
    signingConfigs {
        create("family") {
            storeFile = file("hearth-family.jks")
            storePassword = "hearth-family"
            keyAlias = "hearth"
            keyPassword = "hearth-family"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("family")
        }
        release {
            signingConfig = signingConfigs.getByName("family")
            // Shrinking stays off until it has been checked on a real tablet (Room, serialization, Ktor).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":ui"))
    // Smarter listening: the sentence model runs on the tablet itself.
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
