# Data Safety — borrador final para cargar en Play Console

Revisar las preguntas exactas que presente Play Console contra el AAB final antes de enviar.

## Datos recopilados por H.E.L.P / backend

- Información personal: nombre y email. Se transmiten cifrados en tránsito a Supabase para creación y administración de cuenta.
- Actividad de la app / interacciones: registro de cada activación confirmada de PEDIR AYUDA, con fecha/hora, usuario y versión de la app. Uso: funcionalidad, administración y métricas internas del servicio.
- Identificador de usuario: identificador de Supabase asociado a la cuenta.
- Información de suscripción: estado de titularidad, producto y fecha de vencimiento. El token de compra no se conserva en texto plano; el backend guarda un hash después de validarlo.

No se venden datos y no se usan para publicidad.

## Datos que H.E.L.P no recopila en su backend

- Ubicación histórica: la ubicación puntual se usa en el dispositivo para formar el link de Google Maps del SMS. No se sube a Supabase.
- Agenda/contactos: el usuario elige teléfonos mediante el selector del sistema; los contactos de emergencia quedan locales en el dispositivo.
- Contenido de SMS o historial de llamadas: la app no los lee.
- Datos de tarjeta: son procesados por Google Play y la app no accede a ellos.

## Eliminación

La eliminación de cuenta está disponible dentro de la app y en una URL pública. El borrado elimina la cuenta y datos asociados del backend.

## Ubicación enviada por el usuario

El enlace de ubicación se envía a los contactos de emergencia elegidos por el propio usuario como parte de una acción explícita de PEDIR AYUDA. Revisar la redacción exacta del formulario de Data Safety vigente al momento de enviar, especialmente sus reglas sobre transferencias iniciadas por el usuario.
