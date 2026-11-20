# US-TKT-20 — Consultar el reporte de un evento

## Scope

- Exponer `GET /api/events/{eventId}/report` en `ticket-service`.
- Informar, por sector y para el total del evento, entradas vendidas, devueltas, ocupación y recaudación.
- Contar `ISSUED` y `USED` como vendidas, y `REFUNDED` como devueltas.
- Calcular ocupación sobre la capacidad de cada sector, sin contar reservas, y devolver el porcentaje con dos decimales.
- Calcular la recaudación con los precios de las entradas no reintegradas.
- Permitir la consulta a organizadores con `REPORT_VIEW` solo para eventos de su organización.

## Out of scope

- Exportar reportes, crear dashboards o agregar filtros por fecha.
- Consultas agregadas para múltiples eventos.
- Guardar snapshots o copias persistidas del reporte.
- Cambiar los flujos de compra, devolución o publicación de eventos.

## Criterios de aceptación

- Para cada sector, se informan las entradas vendidas (`ISSUED` y `USED`) y devueltas (`REFUNDED`), porcentaje de ocupación y recaudación, además del total del evento.
- La ocupación cuenta solo entradas vendidas, no reservas.
- La recaudación suma los precios de las entradas vendidas y excluye las entradas `REFUNDED`.
- Un organizador solo puede ver eventos de su organización; si intenta ver otro, recibe `403 forbidden`.
- El endpoint requiere el permiso `REPORT_VIEW`.
- La capacidad de cada sector se obtiene del `event-service`.

## Casos de test

- Un sector con entradas `ISSUED` y `USED` informa ambas como vendidas y suma sus precios a la recaudación.
- Las entradas `REFUNDED` se cuentan como devueltas, pero no como vendidas ni en la recaudación.
- Las entradas `RESERVED` y `VOIDED` no alteran ventas, devoluciones, ocupación ni recaudación.
- La ocupación se calcula con las entradas vendidas divididas por la capacidad del sector, no con las reservas, y se devuelve con dos decimales.
- Un sector sin ventas aparece con métricas en cero y con la capacidad recibida del `event-service`.
- Los totales del evento coinciden con la suma de las métricas de sus sectores.
- Un organizador con `REPORT_VIEW` puede consultar el reporte de un evento de su organización.
- Un organizador con `REPORT_VIEW` que consulta un evento de otra organización recibe `403 forbidden`.
- Un usuario sin `REPORT_VIEW` no puede consultar el endpoint.

## Log

| Cambio | Evidencia |
|---|---|
