# 152: US-NOT-01: Recibir la confirmación de la compra

**Tipo:** Story
**Estado:** implementada; falta correr `PurchaseDeadLetterLogRabbitIntegrationTest` y la prueba de punta a punta (ver [`docs/tasks/152-verificacion-pendiente.md`](../tasks/152-verificacion-pendiente.md))
**Funcionalidad:** Épica 8: Preferencias, recomendaciones, notificaciones y reportes (#8)

Servicio: notification-service · Involucra: ticket-service, auth-service · Consigna: 11 · Entrega 2

## Historia
Como **cliente**,
quiero **recibir un mail cuando se confirma mi compra, con mis entradas y sus QR**,
para **tenerlas a mano el día del evento**.

## Contexto
notification-service ya arma y envía el mail de la compra (#144) con la imagen del QR de cada entrada (#145), pero nada lo dispara. ticket-service va a publicar `purchase.confirmed` (US-TKT-24, #67). Falta escucharlo, no mandar el mismo mail dos veces y reintentar si algo falla.

## Criterios de aceptación
- **CA1: Mail de la compra**
  Dado un mensaje `purchase.confirmed` de una orden `PAID`
  Cuando lo recibe notification-service
  Entonces el comprador recibe un mail con el evento y sus entradas, cada una con su QR
- **CA2: Sin mails repetidos**
  Dado un mensaje con un `messageId` que ya se procesó
  Cuando llega otra vez, aunque sea a la vez a dos instancias
  Entonces no se envía otro mail
- **CA3: Reintento si algo falla**
  Dado que ticket-service, auth-service o el SMTP no responden
  Cuando se procesa el mensaje
  Entonces se intenta hasta 3 veces en total con espera creciente, y si sigue fallando el mensaje pasa a la cola `notification.purchase.dlq` y queda en el log con el id de la orden
- **CA4: Reintentar no cuenta como procesado**
  Dado un intento que falló
  Cuando se reintenta
  Entonces el `messageId` no figura como procesado y el mail se envía
- **CA5: Sin compra que avisar**
  Dado que la orden no existe o no está pagada, o el usuario se dio de baja
  Cuando se procesa el mensaje
  Entonces no se envía nada ni se reintenta, el `messageId` queda procesado y queda un `WARN` en el log
- **CA6: Mensaje que no se puede leer**
  Dado un mensaje con un JSON inválido o sin `orderId` o `userId`
  Cuando llega
  Entonces va directo a `notification.purchase.dlq`, sin reintentos

## Fuera de alcance
- Push u otros canales que no sean el mail.
- Reprocesar a mano los mensajes de la DLQ.
- Los avisos de cancelación (US-NOT-02) y de artistas favoritos (US-NOT-03).

## Notas técnicas
notification-service declara su cola `notification.purchase`, el binding a `ticketya.events` con la clave `purchase.confirmed`, y la DLQ. El `messageId` procesado se guarda en una tabla de notification-service (relacionado con #24) en la misma transacción del envío: si el envío falla, la fila se deshace y el reintento no lo ve como procesado. Depende del enabler RabbitMQ base (#151) y de US-TKT-24 (#67).

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [x] Review aprobada
- [x] Documentación afectada actualizada (`notification-service.md`, `communication.md`)
