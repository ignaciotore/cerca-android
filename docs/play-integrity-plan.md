# Play Integrity — plan preparado

No se implementa una verificación falsa del lado cliente. Google indica que los tokens/veredictos deben verificarse mediante un backend seguro.

Cuando la app exista en Play Console y se pueda vincular el proyecto de Google Cloud:
1. Vincular Play Integrity para com.help.seguridad.
2. Crear la cuenta de servicio del backend.
3. El backend genera/verifica requestHash/nonce y decodifica el token con Google.
4. Usar el veredicto para proteger derechos de suscripción/trial y acciones de servidor, nunca para impedir que una alerta local ya iniciada continúe por un fallo de red.
5. Añadir Google Play Developer API + Real-time Developer Notifications para el estado de suscripciones.

Esto es una dependencia real de Play Console/credenciales, no código que deba incrustarse de forma insegura en el APK.
