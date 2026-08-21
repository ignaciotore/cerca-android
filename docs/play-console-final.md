# CERCA — secuencia final para Play Console

## Datos fijos
- Nombre: CERCA
- applicationId: `com.help.seguridad`
- targetSdk: 36
- Próxima versión: versionCode 31 / versionName 8.9
- Suscripción: `help_monthly`
- Sin anuncios.
- Sin ubicación en segundo plano.
- Sin lectura general de contactos.
- Sin lectura de SMS ni historial de llamadas.

## Antes de producción
1. Usar cuenta de desarrollador **Organización** por las funciones de ficha médica y recordatorios de medicación.
2. Verificar D‑U‑N‑S y datos de la organización.
3. Actualizar la app con la nueva secuencia física Vol– / Vol+ / Vol– / Vol+.
4. Generar AAB firmado con versionCode 31.
5. Completar la declaración de Apps de salud:
   - Administración y servicios de atención médica.
   - Administración de medicamentos y tratamientos.
6. Completar declaración AccessibilityService:
   - No es herramienta de accesibilidad.
   - Uso: funcionalidad de la aplicación.
   - No recopila/ comparte datos mediante AccessibilityService.
   - Adjuntar video de habilitación y prueba.
7. Completar declaración SEND_SMS:
   - Excepción “Alertas de emergencia o seguridad física para enviar SMS”.
   - Adjuntar video mostrando el flujo central de seguridad.
8. Completar Data Safety incluyendo información de salud de CERCA ID.
9. Confirmar política de privacidad pública con información de salud y AccessibilityService.
10. Configurar acceso para revisión con credenciales válidas.
11. Cargar ficha, icono, feature graphic y capturas reales de esta versión.
12. Verificar suscripción `help_monthly` y Billing.
13. Ejecutar prueba interna y cerrada según los requisitos que Play Console muestre para la cuenta.
14. Solicitar revisión y usar publicación administrada si se quiere controlar el momento de salida.

## Pruebas mínimas antes del AAB
- Volumen normal en YouTube/Spotify/video: no debe activar CERCA.
- Vol– / Vol+ / Vol– / Vol+ en menos de 3 segundos: debe abrir la cuenta regresiva.
- Secuencia incompleta o lenta: no debe activar.
- Mantener una tecla apretada: no debe activar.
- CANCELAR: no envía SMS ni llamada.
- Pulsar cualquier volumen durante la cuenta regresiva: cancela.
- Modo prueba: confirma sin SMS ni llamada.
- Widget: abre la misma cuenta regresiva.
- PEDIR AYUDA: ubicación puntual + SMS + llamada.
- Ficha médica, CERCA ID y recordatorios: funcionamiento y textos de descargo visibles.
