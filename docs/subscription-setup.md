# Suscripción H.E.L.P — Play Console

La app queda preparada para Google Play Billing Library 9.1.0.

- Product ID esperado: `help_monthly`
- Tipo: suscripción
- Base plan: mensual con renovación automática
- Precio: definir en Play Console
- La app conserva el período gratuito local de 30 días del MVP.
- Al abrir/volver a la app se restauran compras activas mediante `queryPurchasesAsync`.
- Las compras iniciales se reconocen (`acknowledgePurchase`).

## Pendiente cuando Play Console esté habilitado
Crear el producto `help_monthly` y un base plan activo. Para una etapa de escala/antifraude, sumar validación de compras y derechos en un backend seguro con Google Play Developer API.
