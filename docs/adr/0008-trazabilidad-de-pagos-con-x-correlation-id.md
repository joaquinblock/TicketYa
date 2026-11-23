# 0008: Trazabilidad de pagos con X-Correlation-ID

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
