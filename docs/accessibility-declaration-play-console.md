# CERCA — declaración AccessibilityService para Play Console

## Clasificación
CERCA **no es una herramienta de accesibilidad**.

## Motivo de uso
Seleccionar: **Funcionalidad de la aplicación**.

Texto sugerido:
> CERCA utiliza AccessibilityService únicamente para detectar una secuencia voluntaria de botones físicos de volumen (Volumen –, Volumen +, Volumen –, Volumen +) que permite iniciar el flujo de pedido de ayuda sin abrir la app. El servicio no lee el contenido de la pantalla, no realiza acciones dentro de otras apps y no recopila ni comparte datos mediante AccessibilityService.

## ¿Recopila o comparte datos personales o sensibles mediante AccessibilityService?
**No.**

## Aviso destacado dentro de la app
Antes de abrir Ajustes de Accesibilidad, la app muestra:
> Para detectar la secuencia Volumen – / Volumen + / Volumen – / Volumen + aunque CERCA no esté abierta, Android exige habilitar un Servicio de accesibilidad. CERCA usa este servicio únicamente para detectar esas teclas y reconocer la secuencia de emergencia. No lee el contenido de la pantalla, no controla otras apps y no recopila ni comparte datos mediante este servicio.

Botones:
- AHORA NO
- ACEPTO Y CONTINUAR

## Video para revisión
Grabar un video breve mostrando:
1. Abrir CERCA.
2. Entrar a Acceso rápido.
3. Activar “SOS con botones de volumen”.
4. Mostrar el aviso destacado y tocar “ACEPTO Y CONTINUAR”.
5. Habilitar CERCA en Accesibilidad.
6. Volver a CERCA y usar “PROBAR SECUENCIA”.
7. Presionar Vol– / Vol+ / Vol– / Vol+ y mostrar “Prueba correcta”.
