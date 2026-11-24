# Observabilidad: Trazabilidad y Logs Centralizados

> Cómo seguir operaciones distribuidas en TicketYa usando `X-Correlation-ID`, logs estructurados y Zipkin.

## 1. Trazabilidad visual con Zipkin

Por defecto, la exportación de trazas está deshabilitada en desarrollo (`management.tracing.export.zipkin.enabled: false`) para no depender de un colector al correr servicios con `spring-boot:run`.

Para habilitar el panel visual de trazabilidad, levantá el sistema completo sumando el overlay `observability.compose.yaml` desde la raíz de `TicketYa`:

```bash
docker compose -f docker-compose.yml -f observability.compose.yaml up --build
```

- **Panel web:** http://localhost:9411
- **Qué activa el overlay:**
  - Contenedor `openzipkin/zipkin:3.5.0` en el puerto `9411` (`STORAGE_TYPE: mem`, límite ~320 MB).
  - `MANAGEMENT_TRACING_EXPORT_ZIPKIN_ENABLED: "true"` y muestreo al `100%` (`1.0`) en `api-gateway` y todos los microservicios.
  - Observación de RabbitMQ (`SPRING_RABBITMQ_TEMPLATE_OBSERVATION_ENABLED` y `SPRING_RABBITMQ_LISTENER_SIMPLE_OBSERVATION_ENABLED`), para ver la traza completa desde el gateway hasta `notification-service`.
- **Cómo buscar una operación:**
  - Por nombre de servicio (`serviceName`), o
  - Pegando el `traceId` obtenido de cualquier línea de log.
- **Nota:** las trazas se almacenan en memoria y se pierden al bajar el contenedor. Los endpoints `/actuator/health` están excluidos del trazado (`TracingAutoConfiguration`).

## 2. Centralización de logs (`X-Correlation-ID` + JSON)

Siguiendo el [ADR 0010](../adr/0010-trazabilidad-con-x-correlation-id.md), no se agrega un colector pesado (como Loki o Elasticsearch) al Compose por restricciones de memoria. En cambio:

- **Formato estructurado en Docker:** cada `docker-compose.yml` configura `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash`, emitiendo logs JSON a `stdout`. (En local con `spring-boot:run` la salida sigue en texto legible.)
- **Propagación de `X-Correlation-ID`:**
  - Nace en `api-gateway` (`CorrelationIdFilter`, máxima prioridad), o conserva el header `X-Correlation-ID` enviado por el cliente (1–128 caracteres `[A-Za-z0-9._:-]`).
  - Se devuelve al cliente en la respuesta HTTP y se propaga automáticamente a todos los servicios por HTTP y headers de RabbitMQ.
- **Campos incluidos en cada log de request / operación:**
  - `correlationId` y `traceId` (para cruzar logs con Zipkin)
  - `method`, `endpoint`, `userId`, `status`, `durationMs`
  - `operation` en operaciones críticas (`purchase`, `refund_request`, `event_cancellation`, `check_in`)
  - Nivel `ERROR` para `5xx` y `WARN` para `4xx` en `TicketYaException`.

### Comandos útiles en Docker Compose

- **Ver todos los logs centralizados en vivo:**
  ```bash
  docker compose logs -f
  ```
- **Seguir una operación por su `correlationId` o `traceId`:**
  ```bash
  docker compose logs | grep '"correlationId":"demo-compra-1"'
  ```
  En PowerShell:
  ```powershell
  docker compose logs | Select-String "demo-compra-1"
  ```
- **Filtrar errores críticos (`5xx`):**
  ```bash
  docker compose logs | grep '"level":"ERROR"'
  ```
