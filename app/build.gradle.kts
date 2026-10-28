plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val verCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: 1
val hasKeystore = System.getenv("KEYSTORE_FILE") != null

android {
    namespace = "com.example.aifiles"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.aifiles"
        minSdk = 21          // Android 5.0+ (covers >99% of active phones)
        targetSdk = 34
        versionCode = verCode
        versionName = "1.0.$verCode"
    }
    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(System.getenv("KEYSTORE_FILE"))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Uses your own keystore if provided, otherwise the debug key so the APK is always installable.
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.0")
}
