# RFC-002: Contratos compartidos entre microservicios (módulos `*-api`)

- **Estado:** Abierto — a evaluar cuando se implemente el primer client real (ticket-service → event-service)
- **Relacionado:** #15 (manejo de errores común), `microservices.md` (Comunicación entre microservicios)

## Contexto

Los servicios se comunican de forma síncrona con HTTP Interface clients (`@HttpExchange`). Hoy cada client es una interfaz vacía en el paquete `client` del servicio que llama (p. ej. `TicketClient` en event-service), y los DTOs que viajan se tendrían que declarar dos veces: en el servicio que responde y en el que consume.

Eso trae el mismo problema que tener tipos duplicados entre un back en NestJS y un front en React: si se cambia un campo de un lado y no del otro, nadie se entera hasta que falla en ejecución.

## Propuesta

Un módulo Maven de contrato por cada servicio que expone endpoints a otros servicios. Cada módulo tiene la interfaz `@HttpExchange` y los DTOs que viajan por ella:

```
event-api/
  EventApi.java            @HttpExchange("/events"), @GetExchange("/{id}") EventResponse getById(Long id)
  dto/EventResponse.java

event-service   → EventController implements EventApi
ticket-service  → registra EventApi como client en HttpClientsConfig
```

La misma interfaz sirve de los dos lados. Spring permite que un controller implemente una interfaz `@HttpExchange` y mapea sus endpoints a partir de ella. Si se cambia un campo o una ruta, se cambia una sola vez, y si alguno de los dos lados queda desalineado, el proyecto no compila.

| Módulo | Lo consume |
|---|---|
| `auth-api` | event-service, ticket-service |
| `event-api` | auth-service, ticket-service (y en la Entrega 2: queue, notification, recommendation) |
| `ticket-api` | event-service (y en la Entrega 2: recommendation) |
| `payment-api` | ticket-service |

## Reglas si se adopta

| Regla | Por qué |
|---|---|
| Solo van los endpoints y DTOs que **otro servicio** consume, no toda la API pública | Mientras menos se comparte, menos se acoplan los deploys |
| **Nunca** entidades JPA ni nada de persistencia | El contrato no puede depender del modelo interno de un servicio |
| Cambios compatibles: agregar campos sí, renombrar o sacar no, salvo que se actualicen todos los consumidores en el mismo PR | Evita romper a un servicio que todavía no se redesplegó |
| El módulo no depende de Spring Boot, solo de `spring-web` y de las anotaciones de validación | Es una librería liviana y no arrastra auto-configuración |

## Opciones

| Opción | A favor | En contra |
|---|---|---|
| **Módulos `*-api` compartidos** (la propuesta) | Un solo lugar por contrato, chequeo en compilación, sin herramientas extra. Encaja con el monorepo y el parent POM | Acopla en compilación a quien expone con quien consume. Si se abusa, se vuelve un "monolito distribuido" |
| **Duplicar los DTOs en cada servicio** (lo de hoy) | Servicios totalmente independientes | Los cambios se hacen a mano en dos lugares y los errores aparecen recién en ejecución |
| **OpenAPI contract-first** (`openapi-generator`) | Estándar, independiente del lenguaje, sirve también para el front | Hay que escribir y mantener YAML, y suma pasos de generación al build. Es demasiado para el tamaño del proyecto |
| **Tests de contrato** (Pact / Spring Cloud Contract) | Lo que usan equipos grandes con repos separados | Hay que montar infraestructura de tests. No se justifica con un solo equipo y un monorepo |

Con el front el contrato es otro: el OpenAPI unificado que expone el api-gateway. Con `openapi-typescript` se pueden generar los tipos TS del cliente React. Es independiente de esta propuesta.

## Decisión

Pendiente. Si se adopta, se arranca con `event-api` cuando se implemente `EventClient` en ticket-service, y se registra como ADR.
