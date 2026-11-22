# #152 US-NOT-01: verificación pendiente

El PR de `feat/152-confirmacion-de-compra` se sube sin terminar de verificar. En la review (`/rdd`) el disco quedó sin espacio y Docker dejó de responder. Falta lo siguiente, en este orden.

## 1. Tests que no corrieron

Después de los cambios de la review solo corrió `MailTimeoutTest`, que da verde. Falta correr:

- `PurchaseDeadLetterLogRabbitIntegrationTest` (nuevo, necesita el broker). Verifica que el `ERROR` de un mensaje que va a `notification.purchase.dlq` tenga el `messageId` y el id de la orden (CA3) y que no tenga el email del comprador, aunque venga en la respuesta del SMTP. La corrección (`RabbitConfig.rootCauseType`, que deja solo la clase de la causa) se commiteó sin verla correr. Si el test falla, el problema está ahí.
- La suite completa del servicio, para confirmar que `ddl-auto: update` y los timeouts SMTP no rompieron nada:

```
./mvnw -pl notification-service -am test
```

Con Docker prendido: usa MySQL (`notification-service/compose.yaml`) y RabbitMQ (`rabbitmq.compose.yaml`, que el test levanta si no está arriba). No corras la suite con notification-service levantado, porque los dos consumirían de la misma cola.

## 2. Prueba de punta a punta

Prompt para un agente, o pasos para hacerla a mano. Supone que en `main` está todo menos este PR:

```
Hacé la prueba de punta a punta de US-NOT-01 (#152, PR de feat/152-confirmacion-de-compra) en TicketYa.
Contexto: docs/tasks/152-verificacion-pendiente.md y docs/architecture/notification-service.md.

1. Hacé checkout de feat/152-confirmacion-de-compra con main mergeado (o rebaseado).
2. Corré primero la sección 1 de docs/tasks/152-verificacion-pendiente.md (tests). Si algo da rojo, frená y avisá.
3. Levantá rabbitmq.compose.yaml (raíz) y los compose.yaml de auth, event, ticket, payment y notification
   (notification trae MySQL 3311 y Mailpit). Si falta memoria, arrancá cada servicio con
   -Xmx192m -XX:+UseSerialGC -XX:TieredStopAtLevel=1.
4. Arrancá auth, event, ticket, payment y notification-service.
5. Con un cliente del seeder: login, reservar entradas y pagar la orden hasta que quede PAID.
6. Verificá:
   a. En http://localhost:8025 (Mailpit) llega un mail al comprador con el evento y cada entrada con su QR (CA1).
   b. En el log de notification-service no figura el email del comprador.
   c. En la consola de RabbitMQ (http://localhost:15672) publicá de nuevo el mismo mensaje a ticketya.events
      con la clave purchase.confirmed y el mismo messageId: no sale un segundo mail y el log dice
      "mensaje repetido" (CA2).
   d. Reiniciá notification-service y repetí el paso c: tampoco sale un mail (processed_message sobrevive
      al reinicio).
   e. Pará Mailpit, pagá otra orden y fijate que después de 3 intentos (con 1 s y 2 s de espera) el mensaje
      quede en notification.purchase.dlq, con un ERROR que tenga el messageId y el id de la orden (CA3).
      Volvé a levantar Mailpit y vaciá la DLQ.
7. No cambies código. Anotá el resultado de cada paso (bien o mal, con el log o una captura) en el PR y
   avisá si algo falla.
```

## Nota

El `WARN` que deja `ConditionalRejectingErrorHandler` en cada rechazo imprime el stack completo y puede incluir el email del comprador si lo trae la respuesta del SMTP. No es código del PR y no se tocó.
