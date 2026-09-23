from pathlib import Path

main = Path('app/src/main/java/com/help/seguridad/MainActivity.kt')
s = main.read_text()
s = s.replace('text = "Incluir mi información útil ante una emergencia en el SMS a este contacto"', 'text = "Autorizar mi información útil para este contacto durante una alerta CERCA"')
s = s.replace('toast("Quedó marcado. Cargá tu información útil ante una emergencia para incluirla en el SMS.")', 'toast("Quedó autorizado. Cargá tu información útil ante una emergencia para que esté disponible durante la alerta.")')
s = s.replace('toast("Elegí al menos un contacto para recibir el SMS.")', 'toast("Elegí al menos un contacto para tu Red CERCA.")')
s = s.replace('val smsContacts = savedSmsContacts()\n        homeCallSummary.text = "Llamada: $callLabel"\n        homeSmsSummary.text = "Avisos por SMS: ${smsContacts.size} contacto(s) · Mi Red CERCA suma alertas dentro de la app"', 'homeCallSummary.text = "Llamada: $callLabel"\n        homeSmsSummary.text = "Notificaciones: Red CERCA"')
s = s.replace('"Enviará el SMS, la ubicación y la alerta a tu Red CERCA, pero no realizará la llamada automática."', '"Enviará una notificación a tu Red CERCA con acceso a tu ubicación y a la información útil autorizada, pero no realizará la llamada automática."')
main.write_text(s)

layout = Path('app/src/main/res/layout/activity_main.xml')
x = layout.read_text()
x = x.replace('android:text="Contactos que recibirán el SMS"', 'android:text="Contactos de tu Red CERCA"')
x = x.replace('android:text="Elegí de 1 a 4 contactos. El mensaje incluirá un link puntual de Google Maps."', 'android:text="Elegí hasta 4 personas. Quienes tengan CERCA recibirán una notificación con acceso a tu ubicación."')
x = x.replace('android:text="Avisos por SMS: —"', 'android:text="Notificaciones: Red CERCA"')
x = x.replace('android:text="Tu ubicación puntual se incluye automáticamente en el aviso."', 'android:text="Tu ubicación queda disponible automáticamente dentro de la alerta CERCA."')
layout.write_text(x)
print('UI de notificaciones actualizada')
