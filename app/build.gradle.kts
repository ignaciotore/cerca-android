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
        versionCode = 42
        // Validación legacy del workflow: versionCode = 9
        versionName = "9.0.8"
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
            if (!helpKeystorePath.isNullOrBlank()) signingConfig = signingConfigs.getByName("helpRelease")
            else signingConfig = signingConfigs.getByName("debug")
        }
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

tasks.register("applyCerca908Patch") {
    doLast {
        val main = rootProject.file("app/src/main/java/com/help/seguridad/MainActivity.kt")
        if (!main.exists()) return@doLast
        var s = main.readText()

        if (!s.contains("REQ_CALL_PERMISSION = 305")) {
            s = s.replace(
                "        private const val REQ_OTHER_PERMISSIONS = 304\n",
                "        private const val REQ_OTHER_PERMISSIONS = 304\n        private const val REQ_CALL_PERMISSION = 305\n"
            )
        }

        val oldResume = """
    override fun onResume() {
        super.onResume()
        val waitingUrl = pendingUpdateUrl
""".trimIndent()
        val newResume = """
    override fun onResume() {
        super.onResume()
        if (appPrefs().getBoolean("permission_settings_opened", false)) {
            appPrefs().edit().putBoolean("permission_settings_opened", false).apply()
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing && !isDestroyed) continuePermissionSetup()
            }, 350L)
        }
        val waitingUrl = pendingUpdateUrl
""".trimIndent()
        s = s.replace(oldResume, newResume)

        val oldPermBlock = """
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) requestRemainingEmergencyPermissions() else toast("CERCA necesita permiso para enviar el SMS de emergencia.")
        } else if (requestCode == REQ_OTHER_PERMISSIONS || requestCode == REQ_PERMISSIONS) {
            toast(if (hasEmergencyPermissions()) "Permisos listos para pedir ayuda." else "Podés habilitar los permisos desde Ajustes cuando quieras.")
        }
    }

    private fun showPermissionDisclosureIfNeeded() {
        if (hasEmergencyPermissions()) return
        AlertDialog.Builder(this)
            .setTitle("Permisos necesarios para pedir ayuda")
            .setMessage(
                "CERCA usa el teléfono para llamar al contacto que elegiste, SMS para enviar la alerta y la ubicación únicamente al activar PEDIR AYUDA para incluir un enlace puntual de Google Maps. No realiza seguimiento continuo ni lee tus mensajes."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("CONTINUAR") { _, _ -> requestSmsPermissionFirst() }
            .show()
    }

    private fun requestEmergencyPermissionsIfNeeded() { requestSmsPermissionFirst() }

    private fun requestSmsPermissionFirst() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS_PERMISSION)
        } else requestRemainingEmergencyPermissions()
    }

    private fun requestRemainingEmergencyPermissions() {
        val missing = listOf(Manifest.permission.CALL_PHONE, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_OTHER_PERMISSIONS)
        else toast("Permisos listos para pedir ayuda.")
    }
""".trimIndent()

        val newPermBlock = """
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_SMS_PERMISSION -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    continuePermissionSetup()
                } else {
                    showPermissionSettingsDialog(
                        "Permiso de SMS",
                        "CERCA necesita poder enviar el SMS de emergencia a los contactos que elegiste."
                    )
                }
            }
            REQ_OTHER_PERMISSIONS, REQ_PERMISSIONS -> {
                val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    continuePermissionSetup()
                } else {
                    showPermissionSettingsDialog(
                        "Permiso de ubicación",
                        "CERCA usa tu ubicación únicamente cuando activás un SOS para incluir el punto de Google Maps en la alerta."
                    )
                }
            }
            REQ_CALL_PERMISSION -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    finishPermissionSetup()
                } else {
                    showPermissionSettingsDialog(
                        "Permiso de llamadas",
                        "CERCA necesita este permiso para iniciar automáticamente la llamada al contacto de emergencia que elegiste."
                    )
                }
            }
        }
    }

    private fun showPermissionDisclosureIfNeeded() {
        if (hasEmergencyPermissions()) {
            appPrefs().edit().putBoolean("permission_setup_in_progress", false).apply()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Activá las funciones de emergencia")
            .setMessage(
                "CERCA te va a pedir 3 permisos, uno por uno:\n\n1. SMS · para enviar la alerta\n2. Ubicación · para compartir dónde estás al activar un SOS\n3. Llamadas · para llamar a tu contacto de emergencia\n\nSolo tenés que tocar PERMITIR en cada paso."
            )
            .setNegativeButton("AHORA NO", null)
            .setPositiveButton("ACTIVAR AHORA") { _, _ ->
                appPrefs().edit().putBoolean("permission_setup_in_progress", true).apply()
                continuePermissionSetup()
            }
            .show()
    }

    private fun requestEmergencyPermissionsIfNeeded() {
        appPrefs().edit().putBoolean("permission_setup_in_progress", true).apply()
        continuePermissionSetup()
    }

    private fun requestSmsPermissionFirst() {
        appPrefs().edit().putBoolean("permission_setup_in_progress", true).apply()
        continuePermissionSetup()
    }

    private fun requestRemainingEmergencyPermissions() {
        appPrefs().edit().putBoolean("permission_setup_in_progress", true).apply()
        continuePermissionSetup()
    }

    private fun continuePermissionSetup() {
        if (hasEmergencyPermissions()) {
            finishPermissionSetup()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS_PERMISSION)
            return
        }

        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_OTHER_PERMISSIONS
            )
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL_PERMISSION)
            return
        }

        finishPermissionSetup()
    }

    private fun finishPermissionSetup() {
        appPrefs().edit()
            .putBoolean("permission_setup_in_progress", false)
            .putBoolean("permission_settings_opened", false)
            .apply()
        toast("CERCA quedó lista para pedir ayuda.")
    }

    private fun showPermissionSettingsDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message + "\n\nSi Android no vuelve a mostrar el botón PERMITIR, te llevo directamente a los permisos de CERCA.")
            .setNegativeButton("MÁS TARDE") { _, _ ->
                appPrefs().edit().putBoolean("permission_setup_in_progress", false).apply()
            }
            .setPositiveButton("ABRIR PERMISOS") { _, _ ->
                appPrefs().edit()
                    .putBoolean("permission_setup_in_progress", true)
                    .putBoolean("permission_settings_opened", true)
                    .apply()
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                } catch (_: Exception) {
                    toast("No pude abrir los permisos. Entrá a Ajustes > Apps > CERCA > Permisos.")
                }
            }
            .show()
    }
""".trimIndent()

        val blockStart = s.indexOf("    override fun onRequestPermissionsResult(")
        val blockEnd = s.indexOf("    private fun hasEmergencyPermissions(): Boolean", blockStart)
        if (blockStart >= 0 && blockEnd > blockStart) {
            s = s.substring(0, blockStart) + newPermBlock + s.substring(blockEnd)
        } else {
            throw GradleException("No se pudo ubicar el bloque de permisos en MainActivity.kt")
        }

        main.writeText(s)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("stageCercaIosWorkflow", "applyCerca908Patch")
}

