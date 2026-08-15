# Prueba final H.E.L.P 7.0

## Cuenta
1. Crear cuenta nueva con nombre, email y contraseña.
2. Confirmar email si Supabase lo solicita.
3. Iniciar sesión.
4. Cerrar y volver a abrir la app: la sesión debe permanecer iniciada.
5. Cerrar sesión y volver a entrar.
6. Solicitar recuperación de contraseña y completar el cambio desde la página web.

## Configuración
1. Elegir contacto de llamada desde Android Contacts.
2. Elegir al menos un contacto SMS y hasta cuatro.
3. Verificar que no se solicite READ_CONTACTS.
4. Editar nombre/contactos y verificar persistencia por cuenta.

## Emergencia
1. Mantener PEDIR AYUDA menos de 3 segundos: no debe activarse ni registrar evento.
2. Mantener 3 segundos: debe registrar una sola activación.
3. Verificar SMS con nombre y link de Google Maps.
4. Verificar llamada directa.
5. Repetir sin Internet: SMS/llamada deben continuar; el evento debe quedar en cola.
6. Recuperar Internet y abrir la app: la activación pendiente debe sincronizarse.
7. Simular GPS lento/no disponible: después de ~3,5 segundos debe continuar con SMS y llamada indicando ubicación no disponible si corresponde.

## Panel
1. Entrar con cuenta admin.
2. Ver total de usuarios y activaciones.
3. Confirmar conteo del usuario de prueba.
4. Confirmar que no se muestran ubicación ni teléfonos de emergencia.
5. Intentar entrar con cuenta no-admin: debe devolver acceso denegado.

## Eliminación
1. Eliminar una cuenta de prueba desde la app.
2. Confirmar que ya no puede iniciar sesión y desaparece del panel.
3. Repetir con otra cuenta desde la URL pública de eliminación.

## Google Play (cuando habilite la cuenta)
1. Crear `help_monthly`.
2. Configurar credencial Android Publisher en Supabase.
3. Instalar desde track de prueba, comprar/restaurar suscripción y verificar entitlement de servidor.
