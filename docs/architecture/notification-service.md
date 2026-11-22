# notification-service: mails de la compra y de los cambios de un evento

> US-NOT-01. El servicio escucha `purchase.confirmed` en RabbitMQ (lo publica ticket-service, US-TKT-24), arma el mail de la compra con el QR de cada entrada y lo envía una sola vez por mensaje.
>
> US-NOT-02 (MVP). Escucha los cambios de un evento que publica event-service y avisa a cada comprador una sola vez por cambio. Ver [Avisos de cambios de un evento](#avisos-de-cambios-de-un-evento-us-not-02).

## Cómo llega el aviso

ticket-service publica en el exchange topic `ticketya.events` con la clave `purchase.confirmed` ([ADR 0004](../adr/0004-rabbitmq-para-avisos-entre-servicios.md)):

```json
{ "messageId": "uuid", "occurredAt": "2026-11-21T18:00:00Z", "orderId": 42, "userId": 7, "eventId": 3 }
```

`RabbitConfig` declara la cola `notification.purchase`, su binding y la DLQ `notification.purchase.dlq`. Lo que se rechaza sin requeue pasa a la DLQ por el exchange por defecto (`x-dead-letter-exchange: ""`). También declara el exchange, con los mismos atributos que ticket-service, para que el binding no dependa de qué servicio arranque primero.

`PurchaseConfirmedListener` recibe el mensaje como su propio `PurchaseConfirmedMessage`. El header `__TypeId__` trae la clase de ticket-service, pero el convertidor usa el tipo del parámetro del método.

## Un mail por mensaje

`PurchaseNotificationService.notifyPurchase` hace todo en una transacción: inserta el `messageId` en `processed_message` (INSERT + flush), envía el mail y confirma.

- **Mismo mensaje dos veces, o a dos instancias a la vez:** el segundo INSERT espera a que el primero confirme y falla por la clave. El listener atrapa la `DataIntegrityViolationException`, fuera de la transacción, deja un `INFO` y confirma el mensaje sin enviar.
- **El envío falla:** el rollback borra la fila, así que el reintento no lo ve como procesado.
- **Riesgo aceptado:** si el mail sale y el commit falla, el reintento lo manda otra vez.

`ProcessedMessage` implementa `Persistable`: con un id asignado, `saveAndFlush` haría un `merge`, y una entrega repetida después del commit encontraría la fila, la actualizaría sin error y saldría otro mail.

`processed_message` usa `ddl-auto: update`, a diferencia de los demás servicios (`create`): si se recreara al arrancar, un mensaje que se reentrega después de una caída (mail enviado, sin ack) volvería a mandar el mail.

## Cómo se arma el mail

`PurchaseMailService.send(orderId, userId)` hace tres pasos:

1. Pide la orden a `GET /internal/orders/{id}` del ticket-service: el evento (nombre, fecha, lugar) y las entradas con su ubicación y su `qrCode`. Solo responde órdenes `PAID`.
2. Pide el email y el nombre a `GET /internal/users/{id}` del auth-service.
3. `PurchaseMailComposer` arma un HTML simple, sin motor de plantillas, y se envía con `JavaMailSender`.

## El QR en el mail

`QrCodeGenerator` convierte el `qrCode` de cada entrada (un UUID) en un PNG de 240 × 240 con el núcleo de ZXing (`com.google.zxing:core`). La imagen se arma con `ImageIO`, así no hace falta el módulo `javase` de ZXing.

- **Va como imagen embebida**, no como `data:` en el HTML. El HTML la referencia con `<img src="cid:qr-{ticketId}">` y el mail lleva una parte `image/png` por entrada. Gmail y otros clientes bloquean las imágenes `data:`.
- **Debajo va el código en texto**, por si el cliente de mail no muestra imágenes o el lector del ingreso falla.
- Una entrada sin `qrCode` (por ejemplo, reintegrada) muestra "No disponible" y no lleva imagen.

Los clientes HTTP mandan el secreto interno solos (`common`, US-PAY-14) y tienen timeouts de 1 s para conectar y 2 s para leer.

## Qué pasa si algo falla

| Caso | Qué hace | Por qué |
|---|---|---|
| La orden no existe (404) o no está pagada (409) | No envía, deja un `WARN` y el mensaje queda procesado | No hay compra que avisar: reintentar no cambia nada |
| El usuario no existe o está dado de baja (404) | Igual: no envía, deja un `WARN` y queda procesado | No hay a quién avisar |
| Un servicio responde 5xx o no responde a tiempo | Se reintenta | Es momentáneo |
| Falla el envío por SMTP (`MailException`) o el SMTP no contesta | Se reintenta. El envío tiene límites de 2 s para conectar y 5 s para leer y escribir (`spring.mail.properties.mail.smtp.*`) | Igual que el anterior. Sin límite, el listener quedaría colgado con la transacción abierta |
| JSON inválido, o sin `messageId`, `orderId` o `userId` | Va directo a la DLQ, sin reintentos | Nunca va a poder procesarse |

**Reintento:** 3 intentos en total, con 1 s y 2 s de espera (`spring.rabbitmq.listener.simple.retry`). En Boot 4, `max-retries: 2` cuenta los reintentos, no los intentos. `RabbitConfig` saca del reintento lo que Spring AMQP considera fatal (`DefaultExceptionStrategy`: conversión fallida, entre otros) y el `AmqpRejectAndDontRequeueException` del listener. Al agotar los intentos, el recoverer deja un `ERROR` con el `messageId` y el id de la orden (o del evento, en `notification.event`), y rechaza el mensaje sin requeue, así que pasa a la DLQ de su cola. El reintento y el recoverer son los mismos para las dos colas.

Los mensajes de la DLQ no se reprocesan solos: hay que revisarlos a mano en la consola de RabbitMQ (`http://localhost:15672`).

En el log van el id de la orden y el del usuario, nunca el email ni el QR.

## Avisos de cambios de un evento (US-NOT-02)

event-service publica en `ticketya.events` cuando un evento se cancela, se suspende o se reprograma (reanudado con fecha nueva), con la clave `event.cancelled`, `event.suspended` o `event.rescheduled`:

```json
{ "messageId": "uuid", "occurredAt": "2026-11-22T18:00:00Z", "eventId": 3, "change": "CANCELLED" }
```

`RabbitConfig` declara una sola cola, `notification.event`, con binding `event.*` y DLQ `notification.event.dlq`. El tipo de cambio va en `change`. `EventChangedListener` lo recibe como su propio `EventChangedMessage`; un `change` desconocido no se puede convertir y va a la DLQ sin reintentos.

`EventChangeNotificationService.notifyChange`:

1. Pide el evento a `GET /api/events/{id}` del event-service (nombre, fecha y lugar). Con la fecha que tiene en ese momento: en una reprogramación, la nueva.
2. Pide los compradores a `GET /internal/events/{eventId}/buyers` del ticket-service: los `userId` distintos de órdenes `PAID` del evento. Un comprador con varias entradas u órdenes aparece una sola vez.
3. Por cada comprador, `BuyerNotifier.notifyBuyer` pide el contacto al auth-service, arma el mail con `EventChangeMailComposer` (texto plano) y lo envía.

**Un aviso por comprador.** Cada comprador va en su propia transacción (`BuyerNotifier` es un bean aparte por el proxy de `@Transactional`). Igual que en la compra, antes de enviar inserta en `processed_message` una clave `UUID.nameUUIDFromBytes(messageId + ":" + userId)`, así que no hace falta otra tabla. Si un comprador falla, se sigue con los demás y al final se tira una excepción para que se reintente el mensaje; en el reintento, la clave de los ya avisados choca y se saltean.

| Caso | Qué hace |
|---|---|
| El evento no existe (404) | No envía nada, deja un `WARN` y el mensaje queda procesado |
| El evento no tiene compradores | No envía nada |
| Un comprador se dio de baja (404 del auth-service) | Se saltea con un `WARN` y queda procesado |
| Falla el mail o el contacto de un comprador (5xx, SMTP) | Se sigue con los demás y se reintenta el mensaje: solo salen los que faltan |
| event-service o ticket-service no responden | Se reintenta el mensaje entero |
| JSON inválido, `change` desconocido, o sin `messageId`, `eventId` o `change` | Va directo a la DLQ, sin reintentos |

El texto depende del cambio: una cancelación dice que las entradas pasan a reintegro (lo hace `CancelledEventRefundsJob` del ticket-service, sin que el cliente haga nada); una suspensión, que el evento quedó suspendido; una reprogramación, la fecha nueva. Avisar cuando se acredita el reintegro sería otro mail, fuera del MVP.

## Configuración

| Propiedad | Variable | Por defecto |
|---|---|---|
| `spring.mail.host` / `spring.mail.port` | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `ticketya.mail.from` | `MAIL_FROM` | `no-reply@ticketya.com` |
| `spring.http.serviceclient.ticket-service.base-url` | `TICKET_SERVICE_URL` | `http://localhost:8083` |
| `spring.http.serviceclient.auth-service.base-url` | `AUTH_SERVICE_URL` | `http://localhost:8081` |
| `spring.http.serviceclient.event-service.base-url` | `EVENT_SERVICE_URL` | `http://localhost:8082` |
| `spring.rabbitmq.host` / `spring.rabbitmq.port` | `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |

En desarrollo, el `compose.yaml` del servicio levanta MySQL (puerto `3311`) y **Mailpit**, que recibe los mails sin enviarlos a nadie y los muestra en `http://localhost:8025`. El broker es el de `rabbitmq.compose.yaml`, en la raíz.

## Tests

- `PurchaseConfirmedListenerTest`: validación del mensaje y mensaje repetido.
- `PurchaseNotificationMySqlIntegrationTest`: idempotencia contra MySQL, incluido el mismo mensaje en dos hilos a la vez.
- `PurchaseConfirmedRabbitIntegrationTest`: topología, `__TypeId__` de ticket-service, reintentos y DLQ contra el broker real. Si `rabbitmq.compose.yaml` no está arriba, lo levanta con `docker compose up -d --wait`. Usa la cola real y la vacía antes de cada test: no lo corras con notification-service levantado, porque los dos consumirían de la misma cola.
- `EventChangedListenerTest`: validación del mensaje de un cambio de evento.
- `EventChangeNotificationMySqlIntegrationTest`: un mail por comprador, el mismo mensaje dos veces, el reintento que manda solo los que faltan y el texto de cada cambio, contra MySQL.
- `EventChangedRabbitIntegrationTest`: `__TypeId__` de event-service, DLQ sin reintentos para lo ilegible y el `ERROR` con el id del evento. Mismo cuidado que el de la compra: no correrlo con el servicio levantado.

## Pendiente

- Los avisos de artistas favoritos (US-NOT-03).
- Avisar por otros canales que no sean el mail, o cuando se acredita el reintegro de un evento cancelado.
