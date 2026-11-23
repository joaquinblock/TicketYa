# 0010: Trazabilidad con X-Correlation-ID

> Era el ADR 0008. Se renumeró en #191 porque había otro 0008, el de Docker.

- **Fecha:** 2026-11-23
- **Estado:** aceptada

## Contexto

US-PAY-15 necesita seguir un cobro o un reintegro entre ticket-service y payment-service y registrar su resultado sin exponer datos del medio de pago. El repositorio todavía no tenía propagación de correlación ni un colector común de logs.

## Decisión

ticket-service conserva un `X-Correlation-ID` de 1–128 caracteres ASCII (`[A-Za-z0-9._:-]`) y genera un UUID si falta o no es válido. El cliente HTTP del grupo `payment-service` propaga el valor; payment-service lo asocia al MDC. Ambos servicios emiten logs JSON `logstash` a stdout. Los eventos de cobro y reintegro incluyen la correlación, sus referencias y el resultado; solo el cobro registra el tipo de medio de pago.

La agregación centralizada queda a cargo del entorno que recolecta stdout; esta decisión no agrega un colector al repositorio.

## Alternativas consideradas

- **W3C `traceparent` con OpenTelemetry:** estándar más amplio, pero agrega instrumentación y conceptos que no existen todavía en el proyecto; se puede adoptar en una iniciativa de trazabilidad de todos los servicios.
- **Pasar el ID como parámetro en cada método:** obliga a cambiar firmas de negocio y no cubre llamadas programadas que no nacen de un request HTTP.
- **Agregar un colector al compose:** centralizaría el almacenamiento local, pero excede el alcance de US-PAY-15 y requiere elegir y operar otra infraestructura.

## Consecuencias

- El filtro común crea el contexto MDC en requests HTTP y lo limpia al terminar; el interceptor común añade el header a las llamadas del grupo `payment-service`.
- Las aplicaciones ticket-service y payment-service conservan su compatibilidad cuando un llamador no envía el header.
- Los logs pueden centralizarse sin que los servicios dependan de un backend de observabilidad concreto.

## Ampliación (US-TKT-27)

US-TKT-27 lleva el mismo mecanismo a todos los servicios, en lugar de sumar uno propio:

- La autoconfiguración queda activa por defecto en todos los servicios servlet, salvo el api-gateway. El filtro va con la máxima prioridad, devuelve el id en la respuesta y registra cada request con método, endpoint, usuario, status y duración.
- El interceptor se aplica a todos los clientes HTTP entre servicios, no solo al grupo `payment-service`.
- Los mensajes RabbitMQ llevan el id en el header `X-Correlation-ID` y los listeners lo validan con la misma regla y lo ponen en el MDC mientras procesan el mensaje.
- Fuera de un request, como en los jobs, `CorrelationIds.currentOrNew()` da un id nuevo sin escribirlo en el MDC, para que el hilo del scheduler no arrastre el id de una corrida a las siguientes.

## Ampliación (#191): el gateway y el log centralizado

La consigna pide registrar las operaciones críticas "en un log centralizado". Hasta acá cada servicio escribía en su propia salida, solo ticket-service y payment-service lo hacían en JSON, y el api-gateway no participaba.

- **El id nace en el api-gateway.** El gateway registra `CorrelationIdFilter` a mano (`RequestLogConfig`), con la máxima prioridad.
  - Así quedan registrados también los requests que rechaza: el 401 del JWT, el 503 si no consigue la clave pública y las rutas que no existen.
  - La autoconfiguración de `common` sigue excluyendo al gateway. Lee el usuario de `X-User-Id`, que en el gateway todavía es el del cliente y se puede falsificar. El gateway lo toma del token que validó `IdentityHeadersFilter`, que lo deja en un atributo del request.
- **El id viaja al servicio.** `CorrelationIdFilter` le pasa a la cadena un request con el id en el header, el que llegó o el que generó. Sin eso, el gateway reenviaba el header original, o ninguno, y cada servicio inventaba otro id.
  - `SingleCorrelationIdFilter` evita que el cliente reciba el header dos veces: el del gateway y el que devuelve el servicio.
- **Logs JSON en todos los servicios, solo en Docker.** Cada `docker-compose.yml` pone `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`.
  - El `correlationId` del MDC y los campos del log del request (`method`, `endpoint`, `userId`, `status`, `durationMs`) salen como campos JSON.
  - Con `spring-boot:run` la consola sigue en texto. Por eso ticket-service y payment-service ya no lo fijan en su `application.yml`.
- **El punto central es la salida del compose.** Con todos los servicios en JSON, `docker compose logs` junta los logs de todo el sistema, y una operación se sigue filtrando por su id ([README](../../README.md), *Seguir una operación*).
  - No se agregó un colector (Loki, Elasticsearch) porque son 2 o 3 contenedores más y el sistema ya llega al límite de memoria de las máquinas del equipo.
  - Si hace falta para la defensa, se puede sumar como un perfil de compose que no arranque por defecto.
- **Lo crítico se ve.** Una `TicketYaException` se registra con el nivel de su `ErrorCode`: `ERROR` si es 5xx, `WARN` si es 4xx. Antes todas quedaban en `WARN`, y un evento inexistente se veía igual que la pasarela caída.
  - El nivel sale del status (`ErrorCode.getLevel()`), y un código que sea crítico aunque responda 4xx lo sobrescribe.
  - Se usa `org.slf4j.event.Level` y no un enum propio, porque es lo que ya entiende el logger.
  - Así, `docker compose logs | grep '"level":"ERROR"'` muestra lo crítico de todo el sistema con su `correlationId` ([`error-handling.md`](../architecture/error-handling.md)).
