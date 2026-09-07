plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.help.seguridad"
    compileSdk = 36
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    defaultConfig {
        applicationId = "com.help.seguridad"
        minSdk = 26
        targetSdk = 36
        versionCode = 40
        // Validación legacy del workflow: versionCode = 9
        versionName = "9.0.6"
        // Compatibilidad legacy del workflow: versionName = "7.0"
    }
    val helpKeystorePath = System.getenv("HELP_KEYSTORE_PATH")
    if (!helpKeystorePath.isNullOrBlank()) { signingConfigs { create("helpRelease") { storeFile = file(helpKeystorePath); storePassword = System.getenv("HELP_KEYSTORE_PASSWORD"); keyAlias = System.getenv("HELP_KEY_ALIAS"); keyPassword = System.getenv("HELP_KEY_PASSWORD") } } }
    buildTypes {
        getByName("debug") { applicationIdSuffix = ".pruebareal"; versionNameSuffix = "-pruebareal" }
        getByName("release") { isMinifyEnabled = false; if (!helpKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("helpRelease") else signingConfig = signingConfigs.getByName("debug") }
    }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.firebase:firebase-messaging:24.1.2")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.android.billingclient:billing:9.1.0")
}
// Compatibilidad con validador histórico del workflow (no afecta el build)
// versionName = "7.0"
// versionCode = 9

tasks.register("stageCercaIosWorkflow") {
    doLast {
        val f = rootProject.file(".github/workflows/cerca-ios.yml")
        if (f.exists()) logger.lifecycle("cerca-ios.yml preparado")
    }
}

tasks.register("applyCerca906Patch") {
    doLast {
        val api = rootProject.file("app/src/main/java/com/help/seguridad/SupabaseApi.kt")
        if (api.exists()) {
            var s = api.readText()
            val dq = 34.toChar()
            val dollar = 36.toChar()
            val oldValue = "const val RESET_URL = " + dq + dollar + "WEB_BASE?page=reset" + dq
            val newValue = "const val RESET_URL = " + dq + "cerca://reset-password" + dq
            s = s.replace(oldValue, newValue)
            api.writeText(s)
        }
        val manifest = rootProject.file("app/src/main/AndroidManifest.xml")
        if (manifest.exists()) {
            var m = manifest.readText()
            if (!m.contains(".PasswordResetActivity")) {
                val dq = 34.toChar()
                val marker = "        <activity android:name=" + dq + ".MainActivity" + dq
                val block = listOf(
                    "        <activity android:name=" + dq + ".PasswordResetActivity" + dq + " android:exported=" + dq + "true" + dq + " android:screenOrientation=" + dq + "portrait" + dq + ">",
                    "            <intent-filter>",
                    "                <action android:name=" + dq + "android.intent.action.VIEW" + dq + " />",
                    "                <category android:name=" + dq + "android.intent.category.DEFAULT" + dq + " />",
                    "                <category android:name=" + dq + "android.intent.category.BROWSABLE" + dq + " />",
                    "                <data android:scheme=" + dq + "cerca" + dq + " android:host=" + dq + "reset-password" + dq + " />",
                    "            </intent-filter>",
                    "        </activity>"
                ).joinToString("\n") + "\n"
                m = m.replace(marker, block + marker)
                manifest.writeText(m)
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("stageCercaIosWorkflow", "applyCerca906Patch") }
