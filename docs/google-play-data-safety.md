# H.E.L.P — guía para Seguridad de los datos (Play Console)

Versión objetivo: 6.0.

## Comportamiento del AAB
- Nombre, email y teléfono ingresados por el usuario: almacenados localmente en el dispositivo.
- Contactos de emergencia: seleccionados por el usuario mediante el selector de Android y almacenados localmente.
- Ubicación: se obtiene solo cuando el usuario activa PEDIR AYUDA.
- La ubicación se incluye en un SMS dirigido a los contactos elegidos por el usuario.
- No existe seguimiento de ubicación continuo ni en segundo plano.
- H.E.L.P no lee SMS ni registros de llamadas.
- Perfil, contactos y ubicación no se envían a un servidor propio del desarrollador en esta versión.
- Las compras se procesan mediante Google Play Billing; H.E.L.P no recibe datos de tarjetas.

## Para completar el formulario
Revisar el AAB exacto antes de enviar. Google Play define "recopilar" en función de la transmisión de datos fuera del dispositivo y exige considerar también el comportamiento de SDKs integrados. La transferencia del aviso a contactos es una acción específica de emergencia iniciada por el usuario.
