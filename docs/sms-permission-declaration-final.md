# CERCA — declaración SEND_SMS para Google Play

CERCA solicita `android.permission.SEND_SMS`.

## Caso de uso
Seleccionar la excepción:
**Alertas de emergencia o seguridad física para enviar SMS.**

## Justificación sugerida
> La función principal de CERCA es permitir que una persona pida ayuda rápidamente en una situación de emergencia o seguridad física. Cuando el usuario confirma PEDIR AYUDA, la app envía un SMS a los contactos de confianza previamente configurados. El mensaje incluye un aviso de ayuda y, cuando está disponible, un enlace puntual de Google Maps con la ubicación obtenida en ese momento. La app no lee SMS, no recibe SMS y no utiliza el permiso con fines publicitarios, analíticos ni de mensajería general.

## Evidencia para revisión
El video debe mostrar:
1. configuración previa de contactos;
2. activación de PEDIR AYUDA;
3. cuenta regresiva/cancelación;
4. envío del aviso;
5. que la ficha de Play Store describe el SMS como función central de seguridad.
