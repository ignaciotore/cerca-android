# Eliminación de cuenta

URL pública para Google Play:
`https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/help-web?page=delete`

La página solicita email y contraseña para autenticar al titular. Luego invoca la función protegida `delete-account`, que valida el JWT y elimina el usuario mediante Supabase Auth Admin. Las tablas `profiles`, `help_activations`, `entitlements` y `admin_users` están vinculadas a `auth.users` con eliminación en cascada cuando corresponde.

La app Android incluye la misma opción en Mi cuenta > ELIMINAR MI CUENTA.
