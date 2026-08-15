# H.E.L.P — Checklist final antes de Google Play

## Terminado fuera de Google
- Package definitivo `com.help.seguridad`.
- target/compile SDK 36.
- Backend Supabase de producción en São Paulo.
- Auth real: alta, login, sesión persistente, logout y recuperación.
- Perfil con trial de 30 días ligado a la cuenta y definido por el backend, sin reinicio local del trial.
- Contactos de emergencia locales por usuario.
- Cola offline de activaciones y sincronización al volver Internet.
- Panel administrador privado y métricas globales/por usuario.
- Eliminación de cuenta dentro de app y página web externa.
- Política de privacidad pública actualizada.
- RLS y backend sin avisos de seguridad de Supabase.
- Estructura de entitlements y verificador Google Play desplegado.
- Billing Library 9.1.0 y producto esperado `help_monthly`.
- Flujo de emergencia con timeout de ubicación y llamada aunque falle GPS/SMS.
- Backup Android desactivado; tráfico HTTP claro bloqueado.
- Upload key preparada; el workflow final genera un AAB release firmado cuando los cuatro GitHub Secrets están cargados.
- El acceso usa hora verificada del servidor + reloj monotónico para evitar reinicios simples del trial cambiando la fecha del teléfono.

## Depende de Google Play
- Aprobación/verificación de cuenta de desarrollador.
- Crear aplicación `com.help.seguridad` en Play Console.
- Crear suscripción `help_monthly`, base plan, precio y oferta si corresponde.
- Crear/vincular credencial de Android Publisher y cargarla como secreto `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` en Supabase.
- Activar Play App Signing y cargar el AAB release ya firmado con la upload key.
- Declaración SEND_SMS y revisión de permiso.
- Data Safety, App Content, clasificación de contenido y ficha final.
- Prueba interna y prueba cerrada exigida para la cuenta personal.
