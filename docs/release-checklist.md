# Checklist de publicación — H.E.L.P

## Preparado en código
- `applicationId`: `com.help.seguridad`
- `targetSdk` / `compileSdk`: 36
- Nombre visible: H.E.L.P
- Ícono oficial Android + Play 512x512
- Feature graphic 1024x500
- SMS de emergencia + Google Maps
- Llamada directa
- Selección de contactos mediante selector Android
- Sin tracking en tiempo real
- Disclosure previo de teléfono/SMS/ubicación
- Google Play Billing 9.1.0
- Product ID esperado: `help_monthly`
- Política de privacidad HTML
- Declaración SEND_SMS
- Guía Data Safety
- Workflow de AAB release firmado

## Cuando Google habilite Play Console
1. Crear la app H.E.L.P con package `com.help.seguridad` (confirmar que esté disponible antes del primer AAB).
2. Crear suscripción `help_monthly`, base plan mensual y precio.
3. Publicar `docs/privacy.html` en una URL pública HTTPS y cargar esa URL.
4. Completar App content, Data Safety y la declaración SEND_SMS.
5. Cargar los cuatro GitHub Secrets de firma usando el ZIP de upload key guardado fuera del repo.
6. Ejecutar `Generar H.E.L.P release firmado`.
7. Subir el AAB release a prueba interna/cerrada.
8. Preparar capturas reales de la app con datos ficticios.
9. Para acceso a producción de una cuenta personal nueva: completar la prueba cerrada exigida por Play Console.
