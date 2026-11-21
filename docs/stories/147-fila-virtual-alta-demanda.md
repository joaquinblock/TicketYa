# 147: US-QUE-01: Hacer la fila virtual en un evento de alta demanda

**Tipo:** Story
**Estado:** aprobado
**Funcionalidad:** Épica 7: Fila virtual y control de acceso con QR (#7)

Servicio: queue-service · Involucra: event-service, ticket-service · Sirve a: US-TKT-21 (#64) · Consigna: 6 · Entrega 2

## Historia
Como **cliente**,
quiero **entrar a una fila virtual cuando abre la venta de un evento masivo y saber en qué lugar estoy**,
para **comprar en el orden en que llegué, sin depender de quién refresca más rápido**.

## Contexto
En un evento de alta demanda, miles de clientes intentan reservar en el mismo segundo. Sin fila, gana el más rápido y el ticket-service recibe todo el pico de golpe.

## Criterios de aceptación
- **CA1: Entrar a la fila**
  Dado un evento de alta demanda con la venta abierta (`saleStart` ya pasó)
  Cuando el cliente entra a la fila
  Entonces responde 201 con su estado `WAITING` y su posición (1 es el próximo en habilitarse). Si vuelve a entrar, o entra dos veces a la vez, responde la misma posición
- **CA2: Consultar en qué lugar estoy**
  Dado un cliente esperando en la fila
  Cuando consulta su lugar después de que se habilitó una tanda delante de él
  Entonces responde su estado `WAITING` y una posición menor que la anterior
- **CA3: Habilitación por tandas, con una ventana limitada**
  Dado clientes esperando en la fila
  Cuando pasa el intervalo de tanda (30 s)
  Entonces los siguientes 100 en orden de llegada quedan `ENABLED`, y al consultar reciben su turno y `enabledUntil`, el fin de su ventana para reservar (10 minutos desde que se habilitaron). Consultar otra vez devuelve el mismo turno y el mismo `enabledUntil`: la ventana no se renueva
- **CA4: Ventana vencida**
  Dado un cliente cuyo `enabledUntil` ya pasó
  Cuando consulta su lugar
  Entonces el estado es `EXPIRED`, sin turno. Si vuelve a entrar a la fila, queda al final con una posición nueva
- **CA5: No se puede hacer la fila**
  Dado un evento que no es de alta demanda: 409 `event_not_high_demand`. Antes de `saleStart`: 409 `sale_not_started`. Un evento que no existe: 404 `event_not_found`. Consultar el lugar sin haber entrado: 404 `queue_entry_not_found`
- **CA6: El event-service no responde**
  Dado que el event-service está caído o no responde a tiempo
  Cuando el cliente entra a la fila
  Entonces responde 503 `service_unavailable` y no queda en la fila

## Requisitos no funcionales
- La tanda (100), el intervalo (30 s) y la ventana para reservar (10 min) son configurables.
- Con varias instancias del queue-service, cada cliente tiene una sola posición y una tanda no se habilita dos veces.

## Fuera de alcance
- Avisarle al cliente cuando se habilita: consulta su lugar.
- Ajustar las tandas según las entradas que quedan o cerrar la fila cuando se agota el evento.
- Salir de la fila por voluntad propia.
- Exigir el turno al reservar: es la US-TKT-21 (#64).

## Notas técnicas
Simplificación: las tandas avanzan por tiempo y no por compras terminadas. Una sorted set de Redis por evento, ordenada por llegada, y un cursor `enabledUpTo` que un job sube de a una tanda con `INCRBY` (atómico entre instancias). La posición es el rank menos el cursor. Entrar y consultar piden `TICKET_PURCHASE`. El turno es el JWT de la US-TKT-21 y vence en `enabledUntil`. Reemplaza el modelo de la #23.

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [ ] Review aprobada
- [ ] Simplificaciones justificadas en `docs/architecture/`
- [ ] Documentación afectada actualizada (`endpoints.md`, request de Postman)
