# Declaración SEND_SMS — H.E.L.P

**Permiso:** `android.permission.SEND_SMS`

**Caso de uso de Google Play:** Physical safety / emergency alerts to send SMS.

**Función principal:** H.E.L.P es una aplicación de seguridad personal. El usuario configura previamente contactos de confianza. Al mantener presionado PEDIR AYUDA durante 3 segundos, H.E.L.P envía automáticamente un SMS de emergencia a esos contactos con un enlace puntual de Google Maps y realiza la llamada configurada.

**Por qué es esencial:** El envío automático forma parte de la función central de emergencia. Abrir otra aplicación de SMS y requerir confirmación manual agrega pasos que pueden impedir o retrasar el aviso cuando el usuario necesita ayuda.

**Alcance mínimo:**
- Solicita `SEND_SMS`, pero no `READ_SMS`, `RECEIVE_SMS` ni `WRITE_SMS`.
- No lee mensajes.
- Los destinatarios son elegidos previamente por el usuario.
- El envío se activa por una acción explícita: mantener presionado PEDIR AYUDA durante 3 segundos.
- El contenido se limita al aviso de ayuda y a una ubicación puntual.
- No se usa SMS para publicidad o marketing.

La excepción está sujeta a revisión y aprobación de Google Play.
