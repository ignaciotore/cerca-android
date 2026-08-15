# Play Console — secuencia final H.E.L.P

## Datos fijos
- Nombre: H.E.L.P
- applicationId: `com.help.seguridad`
- targetSdk: 36
- Suscripción: `help_monthly`
- Política de privacidad pública: `https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/help-web?page=privacy`
- Eliminación de cuenta web: `https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/help-web?page=delete`
- Sin anuncios.
- Sin ubicación en segundo plano.
- Sin lectura general de contactos.
- Sin lectura de SMS ni historial de llamadas.

## Cuando Google apruebe la cuenta
1. Crear H.E.L.P en Play Console con paquete `com.help.seguridad`.
2. Activar Play App Signing y registrar la upload key preparada.
3. Crear `help_monthly`, plan base mensual y precio.
4. Crear/vincular credencial Android Publisher API y cargarla en Supabase como `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
5. Generar/cargar el AAB release firmado.
6. Completar App content: privacidad, Data Safety, permisos restringidos/SEND_SMS, anuncios, público objetivo, content rating y acceso a la app.
7. Cargar icono, feature graphic, ficha y capturas reales con datos ficticios.
8. Crear prueba interna y verificar Billing de punta a punta.
9. Crear la prueba cerrada requerida para la cuenta personal y cumplir la cantidad/duración que indique Play Console.
10. Corregir cualquier observación de revisión, solicitar producción y usar publicación administrada si se desea controlar el momento del lanzamiento.

## Ya desarrollado antes de Google
- Auth y cuentas reales.
- Trial de 30 días ligado al usuario.
- Métricas de PEDIR AYUDA + cola offline.
- Panel administrador privado.
- Eliminación de cuenta en app y web.
- Política pública.
- Backend de entitlement Google Play preparado.
- RLS y revisión de seguridad de Supabase sin alertas.
