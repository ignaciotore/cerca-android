# H.E.L.P — Política de privacidad (versión final previa a Google Play)

URL pública preparada:
`https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/help-web?page=privacy`

Última actualización: 15/08/2026.

H.E.L.P utiliza Supabase para autenticación y almacenamiento de los datos básicos de la cuenta (nombre, email, identificador, fechas de alta y estado de prueba/suscripción) y para registrar cada activación confirmada del botón PEDIR AYUDA (fecha/hora y versión de la app). Las contraseñas son gestionadas por Supabase Auth y no son visibles en texto plano para H.E.L.P.

La ubicación se solicita únicamente al activar PEDIR AYUDA y se utiliza para crear un enlace puntual de Google Maps que se envía por SMS a los contactos seleccionados. H.E.L.P no guarda la ubicación histórica en su backend y no realiza seguimiento continuo ni en segundo plano.

Los nombres y teléfonos de los contactos de emergencia quedan guardados localmente en el teléfono. No se suben a Supabase. La app no lee SMS ni historial de llamadas.

Google Play procesa compras y pagos. H.E.L.P no accede a datos de tarjeta. El backend dispone de una tabla de titularidad y una función de verificación para Google Play; la credencial de Android Publisher se configurará cuando Play Console habilite la app.

La cuenta se puede eliminar desde la app o desde la página pública de eliminación. Al eliminar la cuenta se eliminan en cascada el perfil, las activaciones y la titularidad asociada. Los datos locales pueden borrarse desinstalando la app o al eliminar la cuenta desde el dispositivo.

Contacto de privacidad actual: ignacio.tore.deymonnaz@gmail.com
