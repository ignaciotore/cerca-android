# Declaración SEND_SMS — H.E.L.P

Permiso: `android.permission.SEND_SMS`

Caso de uso principal: **Physical safety / emergency alerts to send SMS**.

H.E.L.P es una aplicación de seguridad personal. El usuario configura de uno a cuatro contactos de emergencia. Cuando mantiene presionado el botón PEDIR AYUDA durante 3 segundos, la app solicita una ubicación puntual, arma un enlace de Google Maps y envía automáticamente un SMS de alerta a esos contactos. A continuación inicia la llamada al contacto configurado.

El envío automático es esencial para la función de emergencia porque reduce interacciones adicionales cuando el usuario necesita ayuda.

La app:
- no solicita READ_SMS, RECEIVE_SMS ni permisos de Call Log;
- no lee mensajes;
- no envía publicidad ni comunicaciones comerciales;
- envía SMS únicamente después de la activación explícita de PEDIR AYUDA;
- usa solamente destinatarios seleccionados previamente por el usuario;
- no realiza seguimiento de ubicación en segundo plano.

Video de demostración a preparar con datos ficticios: configurar contactos, mostrar disclosure de permisos, mantener PEDIR AYUDA 3 segundos, mostrar SMS recibido con Maps y llamada iniciada.
