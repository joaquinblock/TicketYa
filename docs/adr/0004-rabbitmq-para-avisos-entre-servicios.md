# 0004: RabbitMQ para los avisos entre servicios

- **Fecha:** 2026-11-20
- **Estado:** aceptada

## Contexto
Cuando se confirma una compra, otros servicios tienen que enterarse (notification-service para el mail, después recommendation-service), pero la compra no puede fallar ni esperar si ellos están caídos. La consigna permite mensajería para notificaciones, y `quality-attributes/communication.md` ya eligió RabbitMQ para la Entrega 2, sin definir cómo se usa.

## Decisión
Los avisos entre servicios van por RabbitMQ, con un exchange topic `ticketya.events` y una clave por tipo de aviso (`purchase.confirmed`). Quien publica declara el exchange; cada servicio que consume declara su propia cola, su binding y su DLQ.

- **Mensaje:** JSON con `messageId` (UUID), `occurredAt` e ids (nunca datos personales ni el QR). Quien consume pide el resto por las rutas `/internal`.
- **Publicar:** después de confirmar la transacción (`@TransactionalEventListener(AFTER_COMMIT)`). Si falla, se loguea y la operación no falla.
- **Consumir:** entrega "al menos una vez". Se descarta un `messageId` ya procesado; un error momentáneo se reintenta 3 veces con espera creciente y después va a la DLQ de esa cola.
- **Contrato:** cada servicio tiene su propia copia del record del mensaje, como con los DTO de los clientes HTTP. No va en `common`.
- **Desarrollo:** el broker se levanta con `rabbitmq.compose.yaml` en la raíz (imagen `rabbitmq` con consola de administración), aparte de los compose de cada servicio, porque es uno solo para todos.

## Alternativas consideradas
- **Kafka:** se justifica con volúmenes enormes o reprocesar eventos; es más difícil de operar (ya descartado en `communication.md`).
- **Llamada HTTP de ticket-service a notification-service:** la compra quedaría atada a que notification-service responda.
- **Outbox transaccional:** garantiza no perder un aviso si el broker está caído al confirmar, pero suma una tabla y un job. Se deja para más adelante; hoy ese caso queda en el log.
- **El contrato en `common`:** acopla el despliegue de los servicios a una misma versión del módulo.
- **El broker en el compose de un servicio:** obliga a levantar ese servicio para que los demás tengan broker, y dos compose con RabbitMQ chocan en el puerto `5672`.

## Consecuencias
- Un servicio puede caerse sin frenar la compra; el aviso espera en la cola.
- Quien consume tiene que ser idempotente: puede recibir el mismo mensaje dos veces.
- Sin outbox, un aviso se pierde si RabbitMQ no está disponible justo al confirmar.
- En desarrollo hay que levantar RabbitMQ antes que los servicios que lo usan.
- Cada servicio pone en un paquete `messaging/` sus eventos internos, los records de sus mensajes y sus publicadores o consumidores, igual que `client/` para HTTP.
