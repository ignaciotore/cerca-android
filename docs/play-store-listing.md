# H.E.L.P — ficha preliminar de Google Play

## Nombre
H.E.L.P

## Descripción corta
Pedí ayuda rápido: llamada directa y SMS con tu ubicación en Google Maps.

## Descripción completa
H.E.L.P simplifica el pedido de ayuda para personas que quieren tener a sus contactos importantes a un toque de distancia.

Configurás una vez:
- a quién llamar;
- qué familiares o contactos recibirán el aviso;
- tus datos básicos.

Cuando necesitás ayuda, mantenés presionado el botón principal durante 3 segundos. H.E.L.P obtiene tu ubicación actual, envía un SMS a tus contactos con un enlace de Google Maps y realiza la llamada al número configurado.

Funciones principales:
- botón de ayuda grande y simple;
- llamada directa al contacto elegido;
- hasta 4 contactos para avisos por SMS;
- selección de contactos por nombre desde la agenda del teléfono;
- ubicación puntual mediante Google Maps;
- perfil editable;
- período de prueba de 30 días.

H.E.L.P está pensada como una herramienta de seguridad personal y conexión con familiares o personas de confianza. No reemplaza a los servicios públicos de emergencia, servicios médicos ni sistemas profesionales de monitoreo.

## Categoría sugerida
Lifestyle / Herramientas (validar en Play Console según opciones vigentes).

## Notas para revisión
La función principal de la app es permitir que una persona solicite ayuda a contactos previamente elegidos. El permiso SEND_SMS se utiliza exclusivamente para enviar el aviso activado voluntariamente por el usuario e incluir su ubicación actual. El permiso CALL_PHONE se utiliza para iniciar la llamada directa al contacto configurado. La ubicación se solicita al activar el botón y no se realiza seguimiento en segundo plano.

## Antes de producción
- Crear app en Play Console.
- Definir package/applicationId final antes del primer lanzamiento público.
- Generar y conservar una upload key.
- Activar Play App Signing.
- Configurar Google Play Billing para la suscripción.
- Completar Data Safety.
- Completar declaración de permisos SMS.
- Publicar URL de Política de Privacidad.
- Crear ícono 512x512, feature graphic y screenshots.
