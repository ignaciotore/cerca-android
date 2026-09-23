from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"No se encontró: {label}")
    return text.replace(old, new, 1)

main = Path('app/src/main/java/com/help/seguridad/MainActivity.kt')
s = main.read_text()

s = replace_once(s, '''        for (i in 1..4) {
            e.putString("sms" + i + "Name", "")
                .putString("sms" + i + "Phone", "")
                .putString("sms" + i + "MedicalAccess", "never")
                .putBoolean("sms" + i + "ShareMedical", false)
        }''', '''        for (i in 1..4) {
            e.putString("sms" + i + "Name", "")
                .putString("sms" + i + "Phone", "")
                .putString("sms" + i + "MedicalAccess", "never")
                .putBoolean("sms" + i + "ShareMedical", false)
                .putBoolean("sms" + i + "Enabled", false)
        }''', 'MainActivity clear slots')

s = replace_once(s, '''            e.putString("sms" + slot + "Name", name)
                .putString("sms" + slot + "Phone", phone)
                .putString("sms" + slot + "MedicalAccess", access)
                .putBoolean("sms" + slot + "ShareMedical", oldShare[phoneKey(phone)] ?: false)''', '''            e.putString("sms" + slot + "Name", name)
                .putString("sms" + slot + "Phone", phone)
                .putString("sms" + slot + "MedicalAccess", access)
                .putBoolean("sms" + slot + "ShareMedical", access != "never")
                .putBoolean("sms" + slot + "Enabled", o.optBoolean("sms_enabled", true))''', 'MainActivity server contact')

s = replace_once(s, '''    private fun savedSmsContacts(): List<SmsRecipient> {
        val callKey = phoneKey(callPhone)
        return listOf(
            SmsRecipient(sms1Name, sms1Phone, contactPrefs().getBoolean("sms1ShareMedical", false)),
            SmsRecipient(sms2Name, sms2Phone, contactPrefs().getBoolean("sms2ShareMedical", false)),
            SmsRecipient(sms3Name, sms3Phone, contactPrefs().getBoolean("sms3ShareMedical", false)),
            SmsRecipient(sms4Name, sms4Phone, contactPrefs().getBoolean("sms4ShareMedical", false))
        ).filter { it.phone.isNotBlank() && (callKey.isBlank() || phoneKey(it.phone) != callKey) }
            .distinctBy { phoneKey(it.phone) }
    }''', '''    private fun savedSmsContacts(): List<SmsRecipient> {
        val p = contactPrefs()
        return listOf(
            SmsRecipient(sms1Name, sms1Phone, p.getBoolean("sms1ShareMedical", p.getString("sms1MedicalAccess", "never") != "never")),
            SmsRecipient(sms2Name, sms2Phone, p.getBoolean("sms2ShareMedical", p.getString("sms2MedicalAccess", "never") != "never")),
            SmsRecipient(sms3Name, sms3Phone, p.getBoolean("sms3ShareMedical", p.getString("sms3MedicalAccess", "never") != "never")),
            SmsRecipient(sms4Name, sms4Phone, p.getBoolean("sms4ShareMedical", p.getString("sms4MedicalAccess", "never") != "never"))
        ).filterIndexed { index, recipient ->
            recipient.phone.isNotBlank() && p.getBoolean("sms${index + 1}Enabled", true)
        }.distinctBy { phoneKey(it.phone) }
    }''', 'MainActivity savedSmsContacts')

main.write_text(s)

family = Path('app/src/main/java/com/help/seguridad/FamilyCircleActivity.kt')
s = family.read_text()
s = s.replace('p.getString("sms"+i+"MedicalAccess","emergency").orEmpty().ifBlank{"emergency"}', 'p.getString("sms"+i+"MedicalAccess","never").orEmpty().ifBlank{"never"}')

s = replace_once(s, '''        for (i in 1..4) {
            e.putString("sms"+i+"Name","")
                .putString("sms"+i+"Phone","")
                .putString("sms"+i+"MedicalAccess","never")
                .putBoolean("sms"+i+"ShareMedical",false)
        }''', '''        for (i in 1..4) {
            e.putString("sms"+i+"Name","")
                .putString("sms"+i+"Phone","")
                .putString("sms"+i+"MedicalAccess","never")
                .putBoolean("sms"+i+"ShareMedical",false)
                .putBoolean("sms"+i+"Enabled",false)
        }''', 'FamilyCircle clear slots')

s = replace_once(s, '''            e.putString("sms"+slot+"Name",name)
                .putString("sms"+slot+"Phone",phone)
                .putString("sms"+slot+"MedicalAccess",access)
                .putBoolean("sms"+slot+"ShareMedical",oldShare[phoneKey(phone)] ?: false)''', '''            e.putString("sms"+slot+"Name",name)
                .putString("sms"+slot+"Phone",phone)
                .putString("sms"+slot+"MedicalAccess",access)
                .putBoolean("sms"+slot+"ShareMedical",access != "never")
                .putBoolean("sms"+slot+"Enabled",o.optBoolean("sms_enabled",true))''', 'FamilyCircle server contact')

s = replace_once(s, '''        val call=phoneKey(prefs().getString("callPhone","").orEmpty())==phoneKey(c.phone)
        box.addView(body((if(call) "Contacto de llamada" else "Contacto SMS") + (if(has) "  ·  Alerta CERCA activa" else "")))''', '''        val call=phoneKey(prefs().getString("callPhone","").orEmpty())==phoneKey(c.phone)
        val sms=rc?.optBoolean("sms_enabled",true) ?: prefs().getBoolean("sms"+c.slot+"Enabled",true)
        val roles=mutableListOf<String>()
        if(call) roles += "Llamada"
        if(sms) roles += "SMS"
        if(has) roles += "Alerta CERCA"
        box.addView(body(roles.joinToString("  ·  ")))
        if(sms) box.addView(body("📍 El SMS de emergencia incluye tu ubicación. 🩺 Información útil: " + if(c.access=="never") "no incluida" else "incluida"))''', 'FamilyCircle role text')

s = s.replace('if (BuildConfig.HEALTH_FEATURES) {', 'run {', 1)
s = s.replace('text = "Ficha médica: " + accessLabel(c.access)', 'text = "Información útil: " + accessLabel(c.access)', 1)
s = s.replace('addView(body(if(has) "CERCA compartirá la ficha según este permiso." else "Este permiso quedará preparado y se activará automáticamente si este contacto instala CERCA. La ficha no se envía por SMS."))', 'addView(body(if(has) "Este permiso se aplica a la alerta CERCA y al SMS de emergencia." else "Si autorizás la información útil, se incluirá en el SMS de emergencia."))', 1)
s = s.replace('addView(secondary("CAMBIAR FICHA MÉDICA") { chooseAccess(c) })', 'addView(secondary("CAMBIAR INFORMACIÓN ÚTIL") { chooseAccess(c) })', 1)
s = s.replace('AlertDialog.Builder(this).setTitle("Ficha médica para "+c.name)', 'AlertDialog.Builder(this).setTitle("Información útil para "+c.name)', 1)

s = replace_once(s, '.put("sms_enabled",!isCall).put("call_enabled",isCall).put("medical_access",c.access)', '.put("sms_enabled",true).put("call_enabled",isCall).put("medical_access",c.access)', 'FamilyCircle sms_enabled')
s = s.replace('.putString("sms"+c.slot+"MedicalAccess","emergency")', '.putString("sms"+c.slot+"MedicalAccess","never")')
s = s.replace('putString("sms"+i+"MedicalAccess","emergency")', 'putString("sms"+i+"MedicalAccess","never")')
s = s.replace('putString("sms"+slot+"MedicalAccess","emergency")', 'putString("sms"+slot+"MedicalAccess","never")')
family.write_text(s)

print('Patch aplicado')
