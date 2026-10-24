plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.onilauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.onilauncher.bgr45"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2"
        ndk { abiFilters += "arm64-v8a" }
    }

    // Android 10+ forbids exec() from app data dirs, so box64 ships as
    // libbox64.so inside jniLibs and is run from nativeLibraryDir.
    // useLegacyPackaging makes Android extract it to disk so it is executable.
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.9")
}
