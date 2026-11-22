# 151: RabbitMQ base para los avisos entre servicios

**Tipo:** Enabler
**Estado:** hecho
**Funcionalidad:** Épica 8: Preferencias, recomendaciones, notificaciones y reportes (#8)

Servicios: ticket-service, notification-service · Entrega 2

## Objetivo
Dejar RabbitMQ instalado y configurado para que un servicio pueda avisar algo a otro sin esperarlo ni depender de él. Es la base de US-TKT-24 (#67) y US-NOT-01 (#152). La decisión está en el ADR 0004.

## Contexto
La comunicación asíncrona con RabbitMQ ya está elegida (`quality-attributes/communication.md`), pero ningún servicio tiene la dependencia ni hay un broker para levantar en desarrollo.

## Criterios de terminado
- **CT1: Broker en desarrollo.** Un `rabbitmq.compose.yaml` en la raíz levanta RabbitMQ con la consola de administración (`docker compose -f rabbitmq.compose.yaml up -d`): AMQP en `5672` y consola en `http://localhost:15672`. El README explica cómo levantarlo y en qué orden.
- **CT2: Dependencia.** ticket-service y notification-service tienen `spring-boot-starter-amqp`, y la conexión sale de `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` y `RABBITMQ_PASSWORD`, con valores por defecto para desarrollo.
- **CT3: Exchange.** Al arrancar, ticket-service declara el exchange `ticketya.events` (topic, durable). Si ya existe, no falla.
- **CT4: Mensajes en JSON.** Los mensajes viajan como JSON con Jackson, y cada servicio tiene su propia copia del contrato (como con los clientes HTTP).
- **CT5: Sin broker no se cae el arranque.** Si RabbitMQ no está levantado, los dos servicios arrancan igual y el health lo muestra.
- **CT6: Tests sin broker.** Los tests que no prueban mensajería corren sin RabbitMQ.

## Fuera de alcance
- Publicar o consumir mensajes concretos: son US-TKT-24 (#67) y US-NOT-01 (#152).
- RabbitMQ en el `docker-compose.yml` general: es la Épica 11 (#11).
- Outbox, clúster o alta disponibilidad del broker.

## Definition of Done
- [x] Cada criterio de terminado se verificó
- [x] Review aprobada
- [x] ADR 0004 aceptado
- [x] Documentación afectada actualizada (`README.md`, `communication.md`, `microservices.md`)
