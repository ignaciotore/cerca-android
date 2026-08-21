# CERCA — Data Safety para Play Console

Revisar las preguntas exactas contra el AAB final antes de enviar.

## Datos recopilados por CERCA / backend
- Información personal: nombre y email para crear y administrar la cuenta.
- Identificador de usuario de Supabase.
- Actividad de la app: activaciones confirmadas de PEDIR AYUDA, fecha/hora y versión, para funcionamiento y métricas internas.
- Información de suscripción: producto, estado y vencimiento.
- Información de salud ingresada voluntariamente en CERCA ID: puede incluir nombre, fecha de nacimiento, grupo sanguíneo, alergias, condiciones médicas, obra social/prepaga, número de afiliado, contacto de emergencia, notas y datos de medicación asociados a la ficha.

No se venden datos y no se utilizan para publicidad.

## Datos locales
- Contactos de emergencia seleccionados por el usuario.
- La agenda completa no se recopila.
- Los recordatorios actuales de medicación (nombre, dosis, días y horarios) se guardan localmente por usuario en el dispositivo.
- La app no lee SMS ni historial de llamadas.

## Ubicación
La ubicación puntual se solicita al confirmar PEDIR AYUDA y se utiliza para formar el enlace de Google Maps enviado a los contactos. No se guarda historial de ubicación en el backend ni se realiza seguimiento continuo/en segundo plano.

## CERCA ID / NFC
Si el usuario activa compartir la ficha médica, el backend genera/usa un identificador aleatorio para permitir la visualización de la ficha de emergencia mediante CERCA ID. El usuario puede desactivar esta función.

## AccessibilityService
El servicio de accesibilidad solo detecta las teclas de volumen para la secuencia de emergencia. No recupera contenido de pantalla y no se usa para recopilar datos personales o sensibles.

## Eliminación
La cuenta puede eliminarse desde la app y mediante la URL pública de eliminación. Los datos backend asociados a la cuenta deben eliminarse según la implementación vigente.
