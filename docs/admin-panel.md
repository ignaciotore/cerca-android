# Panel administrador H.E.L.P

URL:
`https://yduoxeqgxolkzvjexlqk.supabase.co/functions/v1/help-web?page=admin`

Acceso: cuenta autenticada y marcada como administrador en `admin_users`.

Muestra:
- total de usuarios;
- total de activaciones confirmadas de PEDIR AYUDA;
- cantidad de usuarios que activaron ayuda al menos una vez;
- última activación global;
- listado de usuarios con nombre, email, fecha de alta, cantidad de activaciones y última activación;
- búsqueda por nombre/email.

No muestra ni consulta ubicaciones, contactos de emergencia, números telefónicos ni contraseñas. Los datos globales se entregan únicamente a través de la Edge Function `admin-metrics`, que valida el usuario autenticado y su rol de administrador antes de consultar con service role.
