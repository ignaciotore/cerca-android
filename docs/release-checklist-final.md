# CERCA — Checklist final antes de Google Play

## Terminado en código
- Package definitivo `com.help.seguridad`.
- target/compile SDK 36.
- versionCode 31 / versionName 8.9.
- Backend Supabase de producción.
- Auth, perfil y suscripción.
- Contactos de emergencia.
- Flujo PEDIR AYUDA con ubicación puntual, SMS y llamada.
- Acceso rápido físico con secuencia `Vol– / Vol+ / Vol– / Vol+`.
- Cuenta regresiva de 5 segundos para cancelar.
- Widget de acceso rápido.
- Ficha médica CERCA ID y NFC opcional.
- Recordatorios de medicación.
- Eliminación de cuenta.
- Política de privacidad pública.
- AccessibilityService sin lectura de contenido de pantalla.

## Play Console
- Cuenta de desarrollador Organización para las funciones de salud.
- D‑U‑N‑S y verificación de organización.
- Declaración de Apps de salud.
- Declaración AccessibilityService y video demostrativo.
- Declaración SEND_SMS por alertas de emergencia/seguridad física.
- Data Safety incluyendo información de salud.
- Credenciales válidas para revisión.
- Suscripción `help_monthly`.
- AAB release firmado.
- Capturas reales de la versión 8.9.
- Prueba interna y prueba cerrada según lo exigido por Play Console.

## Prueba funcional obligatoria
- Uso normal de volumen no activa SOS.
- Secuencia `– + – +` en menos de 3 segundos sí activa.
- Secuencia lenta/incompleta no activa.
- Mantener volumen apretado no activa.
- Cancelar evita SMS y llamada.
- Modo prueba no envía SMS ni llama.
- Widget activa la misma cuenta regresiva.
