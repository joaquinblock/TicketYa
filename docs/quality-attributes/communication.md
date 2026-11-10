# Comunicación entre servicios

> **Enunciado:** "Utilizar APIs REST, mayormente síncronas, aunque podría utilizarse comunicación asíncrona (fire-and-forget o mensajería) donde se justifique, por ejemplo para notificaciones o para la fila virtual."

## Cómo lo definimos

El criterio fue si quien llama necesita la respuesta para seguir. Si la necesita, la llamada es síncrona. Si solo avisa algo y su operación no debe depender de quien lo recibe, es asíncrona.

## Qué elegimos

| Tipo | Tecnología | Dónde |
|---|---|---|
| Síncrona | **HTTP Interface clients** (`@HttpExchange` + `RestClient`) | Cobrar, reintegrar, consultar datos y permisos, avisos de event-service a ticket-service |
| Asíncrona (Entrega 2) | **RabbitMQ** | Notificaciones y eventos de compra para las recomendaciones |

Reglas que aplican a todos los servicios:

- **Ningún request HTTP dentro de una transacción.** Las llamadas se hacen sin transacción abierta, usando `TransactionOperations`, para no retener conexiones ni bloqueos mientras se espera a otro servicio.
- **La dependencia con payment-service va en un solo sentido.** ticket-service lo llama, y payment-service nunca llama a ticket-service.
- **Errores uniformes.** Un 4xx de otro servicio se reenvía tal cual y un 5xx o una falta de respuesta llegan como `503 service_unavailable`.
- **Trabajo largo fuera del aviso.** Cuando se cancela un evento, ticket-service responde enseguida y un job pide los reintegros, para que muchas entradas no superen el timeout de event-service.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| OpenFeign | Está en modo mantenimiento, y HTTP Interface ofrece el mismo estilo sin dependencias extra |
| WebClient | Es reactivo y los servicios usan MVC |
| RestTemplate | Es la opción anterior a `RestClient` |
| gRPC | El enunciado pide REST |
| Kafka | Se justifica con volúmenes enormes o reprocesamiento de eventos, que no es el caso. RabbitMQ es más simple de operar |

## Estado

- **Implementado:** clients síncronos con sus timeouts, rutas `/internal/**` y manejo común de errores.
- **Pendiente (Entrega 2):** RabbitMQ. Todavía ningún servicio tiene la dependencia.

## Dónde verlo

- [`microservices.md`](../architecture/microservices.md), en *Comunicación entre microservicios*, lista cada client y para qué se usa.
- [`error-handling.md`](../architecture/error-handling.md) describe cómo se propagan los errores.
