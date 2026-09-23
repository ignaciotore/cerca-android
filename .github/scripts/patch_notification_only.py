from pathlib import Path


def once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'No se encontró: {label}')
    return text.replace(old, new, 1)

main = Path('app/src/main/java/com/help/seguridad/MainActivity.kt')
s = main.read_text()

s = once(s, '''    if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS_PERMISSION)
        return
    }

''', '', 'pedido permiso SMS')

s = once(s, '''}    private fun hasEmergencyPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return phone && sms && (fine || coarse)
    }''', '''}    private fun hasEmergencyPermissions(): Boolean {
        val phone = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return phone && (fine || coarse)
    }''', 'hasEmergencyPermissions')

s = once(s, '''    private fun hasSilentEmergencyPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return sms && (fine || coarse)
    }''', '''    private fun hasSilentEmergencyPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }''', 'hasSilentEmergencyPermissions')

s = s.replace('"Necesito permisos de SMS y ubicación."', '"Necesito permiso de ubicación."')
s = s.replace('"Necesito permisos de teléfono, SMS y ubicación."', '"Necesito permisos de teléfono y ubicación."')

old = '''            val message = "CERCA - $safeName necesita ayuda. Ubicacion: $mapsLink"
            startNetworkEmergencyAsync(silent, latitude, longitude)

            emergencyCallPending = !silent
            val smsQueued = sendSmsToContacts(message)

            if (silent) {
                emergencyInProgress = false
                status.text = if (smsQueued) {
                    "SOS silencioso enviado. Tu Red CERCA fue alertada."
                } else {
                    "SOS silencioso activado. No pude confirmar el SMS."
                }
                return@getCurrentLocation
            }

            if (!smsQueued) {
                Handler(Looper.getMainLooper()).postDelayed({ makeDirectCall() }, 700L)
            } else {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (emergencyCallPending && sentSmsParts + failedSmsParts == 0) {
                        appPrefs().edit().putString("last_sms_diag", "SIN CALLBACK DEL MODEM").apply()
                        toast("El teléfono no confirmó el SMS; inicio la llamada igual.")
                    }
                    makeDirectCall()
                }, 8000L)
            }'''
new = '''            startNetworkEmergencyAsync(silent, latitude, longitude)

            emergencyCallPending = !silent
            if (silent) {
                emergencyInProgress = false
                status.text = "SOS silencioso enviado. Tu Red CERCA fue alertada."
                return@getCurrentLocation
            }

            status.text = "Alerta CERCA enviada. Iniciando llamada…"
            Handler(Looper.getMainLooper()).postDelayed({ makeDirectCall() }, 700L)'''
s = once(s, old, new, 'flujo SOS SMS')

s = s.replace('failure = { /* El SMS y la llamada siguen funcionando aunque falle la red CERCA. */ }', 'failure = { /* La llamada local sigue disponible aunque falle temporalmente la red CERCA. */ }')

main.write_text(s)

family = Path('app/src/main/java/com/help/seguridad/FamilyCircleActivity.kt')
s = family.read_text()
s = s.replace('Agregá hasta 4 contactos. Si alguno también usa CERCA, lo detectamos automáticamente y activamos funciones adicionales.', 'Sumá personas a tu Red CERCA. Quienes tengan CERCA instalada reciben notificaciones de emergencia con acceso a tu ubicación y a la información útil que autorices.')
s = s.replace('Los contactos reciben el SMS aunque no tengan CERCA. Si tienen la app, además reciben la alerta prioritaria y las funciones que autorices.', 'Las alertas se envían como notificaciones CERCA. Si una persona todavía no tiene la app, invitala para que pueda recibirlas. Podés mantener un único contacto adicional para llamada.')

old = '''        val call=phoneKey(prefs().getString("callPhone","").orEmpty())==phoneKey(c.phone)
        val sms=rc?.optBoolean("sms_enabled",true) ?: prefs().getBoolean("sms"+c.slot+"Enabled",true)
        val roles=mutableListOf<String>()
        if(call) roles += "Llamada"
        if(sms) roles += "SMS"
        if(has) roles += "Alerta CERCA"
        box.addView(body(roles.joinToString("  ·  ")))
        if(sms) box.addView(body("📍 El SMS de emergencia incluye tu ubicación. 🩺 Información útil: " + if(c.access=="never") "no incluida" else "incluida"))'''
new = '''        val call=phoneKey(prefs().getString("callPhone","").orEmpty())==phoneKey(c.phone)
        val roles=mutableListOf<String>()
        if(call) roles += "Llamada"
        if(has) roles += "Notificación CERCA" else roles += "Notificación pendiente"
        box.addView(body(roles.joinToString("  ·  ")))
        if(has) box.addView(body("📍 Ubicación: disponible desde la alerta. 🩺 Información útil: " + if(c.access=="never") "no autorizada" else "autorizada"))
        else box.addView(body("Esta persona debe instalar CERCA y aceptar tu invitación para recibir notificaciones."))'''
s = once(s, old, new, 'roles FamilyCircle')

s = s.replace('Este permiso se aplica a la alerta CERCA y al SMS de emergencia.', 'Este permiso se aplica a la alerta CERCA que recibe el contacto.')
s = s.replace('Si autorizás la información útil, se incluirá en el SMS de emergencia.', 'Cuando esta persona se vincule a CERCA, podrá ver la información útil que autorices durante la alerta.')
s = s.replace('.put("sms_enabled",true).put("call_enabled",isCall).put("medical_access",c.access)', '.put("sms_enabled",false).put("call_enabled",isCall).put("medical_access",c.access)')

family.write_text(s)
print('Migración a notificaciones aplicada')
