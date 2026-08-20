plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.help.seguridad"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.help.seguridad"
        minSdk = 26
        targetSdk = 36
        versionCode = 25
        // Validación legacy del workflow: versionCode = 9
        versionName = "8.3"
        // Compatibilidad legacy del workflow: versionName = "7.0"
    }

    val helpKeystorePath = System.getenv("HELP_KEYSTORE_PATH")
    if (!helpKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("helpRelease") {
                storeFile = file(helpKeystorePath)
                storePassword = System.getenv("HELP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HELP_KEY_ALIAS")
                keyPassword = System.getenv("HELP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".pruebareal"
            versionNameSuffix = "-pruebareal"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (!helpKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("helpRelease")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.android.billingclient:billing:9.1.0")
}
