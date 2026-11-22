plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.familie.kinderschutz"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.familie.kinderschutz"
        minSdk = 26          // Android 8.0 und neuer
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
// Bewusst keine Zusatz-Bibliotheken: nur Android selbst, damit das Projekt klein und verständlich bleibt.
