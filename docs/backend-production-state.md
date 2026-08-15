# Backend H.E.L.P — estado pre-Google

Proyecto Supabase: `yduoxeqgxolkzvjexlqk` — São Paulo.

## Tablas
- `profiles`: cuenta y trial de 30 días.
- `help_activations`: una fila por activación real del botón, sin ubicación.
- `entitlements`: acceso pago validado por servidor.
- `admin_users` / `admin_email_hashes`: autorización administrativa protegida.

## Edge Functions activas
- `delete-account`: eliminación autenticada de cuenta y datos asociados.
- `admin-metrics`: métricas globales solo para administrador.
- `google-play-entitlement`: verificador de compra, pendiente únicamente de credencial Google.
- `help-web`: privacidad, eliminación web, recuperación de contraseña, confirmación de email y panel administrador.

## Privacidad de diseño
- Ubicación de emergencia: no se persiste en backend.
- Contactos de emergencia: locales por cuenta/dispositivo.
- Contraseña: administrada por Supabase Auth; no se guarda en la app.
- Tokens de sesión Android: cifrados con Android Keystore.
- Token Google Play: backend persiste únicamente hash SHA-256.
- Compra Google Play: vinculada a la cuenta H.E.L.P mediante `obfuscatedAccountId`; una compra de otra cuenta es rechazada.
