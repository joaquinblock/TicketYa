# Lente: comunicación y seguridad

Se corre porque el triage vio un riesgo concreto en el diff. Concentrate en ese riesgo y en lo que el cambio agrega. Leé también `_formato.md`, que está en esta misma carpeta.

## Qué leer

- `docs/quality-attributes/communication.md` y `docs/quality-attributes/api-gateway.md`.
- `docs/architecture/permissions.md`, `docs/architecture/error-handling.md` y los ADR que tocan el tema (`docs/adr/`), en particular el de RabbitMQ y los de JWT.
- El diff y los `client/`, `messaging/`, `config/` y controllers que toca.

## Comunicación entre servicios

1. **El otro servicio no responde o responde mal.** Toda llamada HTTP tiene timeout. Un 4xx se reenvía tal cual y un 5xx o la falta de respuesta llegan como `503 service_unavailable`. ¿Qué ve el usuario y qué queda en la base en cada caso?
2. **Sin transacción abierta** mientras se espera a otro servicio o al broker.
3. **Mensajes:** se publican después del commit, llevan `messageId` para descartar repetidos y llevan solo ids. Con el broker caído, la operación principal sigue y el fallo queda en el log con el id del recurso. Del lado del consumidor: es idempotente, convierte según el tipo del parámetro (no según `__TypeId__`) y tiene una DLQ.
4. **Dirección de las dependencias:** no aparece un ciclo nuevo entre servicios. Por ejemplo, payment-service nunca llama a ticket-service.

## Seguridad

1. **Quién puede llamar:** cada endpoint nuevo tiene el `@PreAuthorize` o la regla que corresponde según `permissions.md`. Las rutas `/internal/**` no salen por el gateway.
2. **De quién es el recurso:** un usuario no puede ver ni cambiar la orden, la entrada o la preferencia de otro cambiando un id en la URL. El id del usuario se toma del token, no del body.
3. **Datos personales:** mails, nombres y tokens no aparecen en los logs ni en los mensajes del broker.
4. **Entrada:** validación de lo que llega (`@Valid`, rangos, tamaños) y nada de armar consultas concatenando strings.
