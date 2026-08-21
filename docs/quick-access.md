# CERCA — acceso rápido de emergencia

El acceso físico usa una secuencia fija para evitar activaciones accidentales:

**Volumen – → Volumen + → Volumen – → Volumen +**

La secuencia debe completarse en menos de 3 segundos.

- Una pulsación normal de volumen no activa el SOS.
- Mantener una tecla apretada no cuenta como varias pulsaciones.
- La app no consume los eventos: el volumen continúa funcionando normalmente.
- Al reconocer la secuencia, CERCA vibra y abre una cuenta regresiva de 5 segundos.
- Durante la cuenta regresiva se puede cancelar con el botón CANCELAR o con una pulsación de cualquiera de las teclas de volumen.
- Existe un modo de prueba que verifica la secuencia sin enviar SMS ni iniciar llamadas.

Para detectar la secuencia cuando CERCA no está abierta se utiliza AccessibilityService. El servicio solo filtra las teclas de volumen, no recupera contenido de ventanas y no recopila datos.
