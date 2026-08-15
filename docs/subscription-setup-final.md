# Configuración de suscripción — Google Play

## IDs fijados
- Paquete: `com.help.seguridad`
- Suscripción: `help_monthly`
- Trial H.E.L.P: 30 días ligados a la cuenta en Supabase, no a la instalación.

## Lo que ya está desarrollado fuera de Google
- Google Play Billing Library 9.1.0 en Android.
- Restauración de compras y acknowledge.
- Envío del purchase token al backend autenticado.
- La compra se inicia con un `obfuscatedAccountId` SHA-256 derivado del ID interno de la cuenta H.E.L.P y el backend exige que Google devuelva ese mismo identificador.
- Edge Function `google-play-entitlement` desplegada en Supabase.
- Verificación preparada contra Android Publisher API `subscriptionsv2`.
- El token de compra se guarda únicamente como hash SHA-256.
- El mismo token de compra no puede asociarse a dos cuentas H.E.L.P diferentes.
- La app concede acceso pago únicamente cuando el entitlement del servidor está activo y no venció.

## Lo único que depende de Google
1. Crear `help_monthly` en Play Console.
2. Crear plan base mensual renovable y definir el precio.
3. No agregar otra prueba gratuita mientras se mantenga el trial H.E.L.P de 30 días, para no duplicar beneficios.
4. Crear/vincular la credencial de Android Publisher API y cargar su JSON en Supabase como secreto `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
5. Probar compra, renovación, cancelación y restauración desde un track de Google Play.

Hasta que exista esa credencial, el endpoint de servidor responde `google_play_not_configured` y no habilita acceso pago por su cuenta.
