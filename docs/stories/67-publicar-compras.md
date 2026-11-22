# 67: US-TKT-24: Publicar compras y cancelaciones

**Tipo:** Story
**Estado:** hecha. Las compras, en el PR de #67; las cancelaciones las publica event-service (no ticket-service), en el PR de US-NOT-02, que cierra #67
**Funcionalidad:** Épica 8: Preferencias, recomendaciones, notificaciones y reportes (#8)

Servicio: ticket-service · Involucra: notification-service, recommendation-service · Consigna: 10, 11 · Entrega 2

## Historia
Como **cliente**,
quiero **que el sistema avise cuando se confirma mi compra**,
para **recibir mis entradas sin depender de que la compra y el aviso funcionen juntos**.

## Contexto
Cuando se paga una orden, las entradas quedan `ISSUED` con su QR, pero ningún otro servicio se entera. notification-service ya sabe armar y enviar el mail de la compra (#144, #145), y le falta el aviso que lo dispare. Esta story cubre solo las compras: los reintegros de un evento cancelado quedan para cuando se haga US-NOT-02.

## Criterios de aceptación
- **CA1: Compra confirmada**
  Dado una orden que se paga
  Cuando se confirma la transacción que la deja `PAID`
  Entonces se publica un mensaje `purchase.confirmed` en `ticketya.events` con `messageId`, `occurredAt`, `orderId`, `userId` y `eventId`
- **CA2: Solo después de confirmar**
  Dado un pago que falla, se rechaza o hace volver atrás la transacción
  Cuando termina el intento
  Entonces no se publica nada
- **CA3: El aviso no rompe la compra**
  Dado que RabbitMQ no está disponible
  Cuando se paga una orden
  Entonces el pago responde igual que siempre (200 con la orden `PAID`) y el fallo queda en el log con el id de la orden
- **CA4: Un pago repetido no se avisa dos veces**
  Dado una orden ya `PAID`
  Cuando se repite el mismo pago (US-TKT-11)
  Entonces no se publica otro mensaje
- **CA5: Avisos repetidos detectables**
  Dado un mismo mensaje entregado más de una vez
  Cuando lo recibe un consumidor
  Entonces tiene el mismo `messageId` y puede descartarlo

## Fuera de alcance
- Publicar los reintegros de un evento cancelado: queda para US-NOT-02. El PR de esta story dice `Part of #67`. Al hacer US-NOT-02 se decidió que la cancelación la publica event-service (`event.cancelled`), que es quien cambia el estado; el ticket-service solo expone los compradores del evento.
- Outbox: si RabbitMQ no está disponible al confirmar, el aviso se pierde y queda en el log.
- Consumir el mensaje: es US-NOT-01 (#152). recommendation-service lo consume en su propia story.

## Notas técnicas
Se publica con `@TransactionalEventListener(phase = AFTER_COMMIT)`, así nunca se avisa de una compra que no se confirmó. El mensaje lleva solo ids: los datos de la orden y del usuario los pide quien consume. Depende del enabler RabbitMQ base (#151).

## Definition of Done
- [x] Cada criterio tiene un test que pasa
- [x] Review aprobada
- [x] Documentación afectada actualizada (`ticket-service.md`, `communication.md`)
