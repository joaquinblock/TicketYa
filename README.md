# TicketYa

> Arquitectura Web (TUDAI) — Díaz Pace/Yañez (2026)

[Consigna completa](#trabajo-práctico-grupal-ticketya).

## Cómo ejecutarlo

### Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| JDK | 25 | Compilar y correr los servicios (`<java.version>25</java.version>` en el `pom.xml` raíz) |
| Maven | El wrapper del repo (`./mvnw`) | No hace falta instalarlo |
| Docker | Docker Desktop corriendo | Cada servicio levanta su propia base con su `compose.yaml` |

> **Si la terminal tiene un JDK más viejo** (por ejemplo, 24), se puede compilar igual con `-Djava.version=24`: el código no usa nada propio del 25. Desde IntelliJ, con el JDK 25 del proyecto, no hace falta.

### Servicios y puertos

| Servicio | Puerto | Base (puerto local) | Entrega |
|---|---|---|---|
| `api-gateway` | **8080** | — | 1 |
| `auth-service` | 8081 | MySQL (3307) | 1 |
| `event-service` | 8082 | MySQL (3308) | 1 |
| `ticket-service` | 8083 | MySQL (3309) | 1 |
| `payment-service` | 8084 | MySQL (3310) | 1 |
| `queue-service` | 8085 | Redis (6379) | 2 (esqueleto) |
| `notification-service` | 8086 | MySQL (3311) | 2 (esqueleto) |
| `recommendation-service` | 8087 | MongoDB (27017) | 2 (esqueleto) |

**Para la Entrega 1 alcanza con el gateway y los cuatro primeros servicios.** Cada servicio arranca su base con Docker la primera vez (`spring.docker.compose`). No hace falta levantarlas a mano.

### Levantarlo

En cinco terminales, desde la raíz del repo y en este orden:

```bash
./mvnw -pl auth-service spring-boot:run
./mvnw -pl event-service spring-boot:run
./mvnw -pl ticket-service spring-boot:run
./mvnw -pl payment-service spring-boot:run
./mvnw -pl api-gateway spring-boot:run
```

En IntelliJ es lo mismo: se corre la clase `*Application` de cada módulo.

- **Todo entra por el gateway (`http://localhost:8080`).** Las rutas `/internal/**` (llamadas entre servicios) no pasan por el gateway: se usan directo contra el puerto de cada servicio.
- **Swagger de todo el sistema:** http://localhost:8080/swagger-ui.html. Arriba a la derecha se elige el servicio.
- **La identidad va en el header `X-User-Id`.** La Entrega 1 no tiene autenticación (ver *Modalidad de entrega*). Los permisos se verifican contra el auth-service (ver [`permissions.md`](docs/architecture/permissions.md)).
- **El primer request a un servicio recién levantado puede tardar unos segundos**, mientras se calienta.
- **La primera vez que se crea el contenedor de una base, MySQL tarda en inicializarse**, y el servicio puede fallar al arrancar con `Communications link failure`. El contenedor queda levantado igual: alcanza con volver a arrancar el servicio.

### Datos de prueba

Cada servicio recrea su base al arrancar (`ddl-auto: create`) y su seeder la vuelve a cargar. **Para empezar de cero alcanza con reiniciar el servicio.**

| Seeder | Qué carga |
|---|---|
| `AuthSeeder` | 9 permisos, 4 roles y 6 usuarios: `1` cliente, `2` organizador de la org 1, `3` admin, `4` personal de acceso, `5` organizador de la org 2 y `6` organizador de la org 1 sin eventos propios. Organizaciones `1` *Del Sur Producciones* y `2` *Cultura Serrana* |
| `EventSeeder` | 5 lugares en 5 ciudades, 239 asientos, 11 participantes y 12 eventos en todos los estados, con fechas relativas al día de arranque (ver *Datos de prueba* en [`event-service.md`](docs/architecture/event-service.md)) |
| `TicketSeeder` | El inventario de los eventos 1 y 2, dos compras pagadas (una con una devolución pendiente) y una reserva en curso (ver [`ticket-service.md`](docs/architecture/ticket-service.md)) |
| `PaymentSeeder` | Los pagos de esas órdenes: aprobados, uno rechazado y uno pendiente |

Los ids entre servicios coinciden (por ejemplo, el evento 1 del event-service es el evento 1 del ticket-service), porque cada seeder asume los ids de los otros.

### Tests

```bash
./mvnw test                      # todos los módulos
./mvnw -pl ticket-service test   # uno solo
```

- **Necesitan Docker:** varios tests corren contra el MySQL real de cada servicio. Dejan la base recreada, así que conviene reiniciar el servicio después.
- **La prueba de no doble venta** que pide la consigna es `ReservationMySqlIntegrationTest`: muchos hilos compran a la vez el mismo asiento, o el último lugar de un sector, y solo uno lo consigue. El mecanismo está en [`concurrency-and-idempotency.md`](docs/quality-attributes/concurrency-and-idempotency.md).

### Colecciones de Postman

Están en [`docs/postman/`](docs/postman/). Se importan en Postman y cada una trae sus variables. Para correr una entera: clic derecho → *Run collection*.

| Colección | Qué cubre | Apunta a | ¿Cambia datos? |
|---|---|---|---|
| `event-search` | Búsqueda de eventos y visibilidad del detalle | Gateway | No: se puede repetir |
| `event-creation` | Alta de eventos y permisos | Gateway | Sí |
| `participants` | ABM de participantes y asignación a un evento | Gateway | Sí |
| `event-lifecycle` | Ver, editar, borrar, publicar y cancelar | Gateway | Sí: **reiniciar el event-service antes** |
| `event-sectors` | Sectores y precios de un borrador | Gateway | Sí: **reiniciar el event-service antes** |
| `event-suspension` | Suspender, reanudar y quién puede hacerlo | Gateway | Sí: **reiniciar el event-service antes** |
| `ticket-service` | Inventario, reserva, pago, compras y devoluciones | `:8083` (y necesita el payment-service) | Crea su propio evento: se puede repetir |
| `payment-service` | Cobros, rechazos, reintentos y reintegros | `:8084` | Crea sus propias órdenes: se puede repetir |

## Documentación

| Tema | Dónde |
|---|---|
| Features y user stories de todo el sistema, con criterios de aceptación | [`docs/user-stories.md`](docs/user-stories.md) |
| Microservicios, responsabilidades y comunicación | [`docs/architecture/microservices.md`](docs/architecture/microservices.md) |
| Diagrama de módulos | [`docs/architecture/modules.md`](docs/architecture/modules.md) |
| Diagrama de secuencia de la compra de una entrada numerada | [`docs/architecture/purchase-sequence.md`](docs/architecture/purchase-sequence.md) |
| DER de cada servicio, con las relaciones entre servicios y los datos solapados | [`auth-service.md`](docs/architecture/auth-service.md), [`event-service.md`](docs/architecture/event-service.md), [`ticket-service.md`](docs/architecture/ticket-service.md), [`payment-service.md`](docs/architecture/payment-service.md) |
| Listado de endpoints | [`docs/architecture/endpoints.md`](docs/architecture/endpoints.md) y Swagger |
| Errores y sus códigos | [`docs/architecture/error-handling.md`](docs/architecture/error-handling.md) |
| Permisos | [`docs/architecture/permissions.md`](docs/architecture/permissions.md) |
| Atributos de calidad (concurrencia, idempotencia, comunicación, persistencia, gateway) | [`docs/quality-attributes/`](docs/quality-attributes/) |
| Decisiones de diseño discutidas | [`docs/rfc/`](docs/rfc/) |

### Especificaciones

No usamos OpenSpec. Cada funcionalidad empezó por una especificación escrita y revisada en un PR antes de implementarse, como pide la *Metodología sugerida*, y esa especificación quedó versionada en el repo:

1. **Requisitos y escenarios:** la user story y sus criterios de aceptación, en `docs/user-stories.md`. Cada una enlaza la consigna que cubre.
2. **Diseño:** el doc del servicio en `docs/architecture/`, y un RFC en `docs/rfc/` cuando había varias opciones para discutir.
3. **Tareas:** las issues de GitHub, agrupadas por épica, y un PR por funcionalidad que las cierra.

## Trabajo Práctico Grupal: TicketYa

*TicketYa* es un startup que busca construir una plataforma de venta de entradas (ticketera virtual) para todo tipo de eventos: recitales, festivales, obras de teatro, partidos y conferencias. La misión es que cualquier organizador, desde un club de barrio hasta una gran productora, pueda publicar sus eventos y vender entradas en forma simple, segura y a bajo costo, y que el público pueda encontrar y comprar entradas desde su celular sin filas ni sobresaltos.

La plataforma trabaja con tres tipos de usuarios: **administradores** (gestión completa, mantenimiento y tareas operativas), **organizadores** (diseñan y publican eventos) y **clientes** (buscan eventos y compran entradas). Además, en el ingreso a cada evento, el **personal de acceso** escanea el código QR de las entradas para validarlas.

El mayor desafío del negocio aparece en los eventos masivos: cuando se abre la venta de un recital convocante, decenas de miles de personas intentan comprar en los mismos minutos. En esas condiciones el sistema debe seguir funcionando, ser justo con quienes llegaron primero y, sobre todo, garantizar que **una misma entrada nunca se venda dos veces**. Con el tiempo, se pretende también personalizar la oferta de eventos según los gustos e historial de cada cliente. Un diagrama de contexto del sistema es el siguiente:

![Diagrama de contexto de TicketYa](docs/img/diagrama-contexto.png)

El trabajo debe centrarse en el **backend**, que orquesta todas las interacciones, si bien deben contemplarse en el diseño los sistemas adicionales (aplicaciones de clientes y organizadores, backoffice administrativo, lectores de acceso, pasarela de pagos, notificaciones, etc.).

## Funcionalidad

1. Cada **evento** tiene: ID, nombre, descripción, tipo (recital, teatro, deporte, conferencia, etc.), organizador, participantes (artistas, equipos, disertantes), fecha y hora, lugar, y estado (borrador, publicado, agotado, cancelado, finalizado).
2. Cada **lugar** (estadio, teatro, sala) tiene: ID, nombre, dirección, ubicación (latitud/longitud) y un conjunto de **sectores**. Un sector puede ser **numerado** (filas y asientos identificados) o **no numerado** (zona con una capacidad máxima, p. ej. “Campo”). Para cada evento, el organizador define el precio de cada sector.
3. Los organizadores crean sus eventos en estado borrador, configuran sectores y precios, y luego los publican. Los administradores pueden suspender o cancelar cualquier evento.
4. Un cliente elige un evento, un sector y, si es numerado, asientos específicos. El sistema **reserva temporalmente** esos lugares (por ej. por 10 minutos) mientras el cliente paga. Si el pago es aprobado, se emiten las entradas con un **código QR único**; si el pago es rechazado o la reserva vence, los lugares se liberan. Se debe registrar el detalle de la compra y el medio de pago, y limitar la cantidad de entradas por cliente para un mismo evento.
5. **Una entrada nunca puede venderse dos veces**, aun cuando muchos clientes intenten comprar el mismo asiento (o el último lugar de una zona) al mismo tiempo, o cuando un cliente reintente el pago por un problema de conexión.
6. Para eventos marcados como de **alta demanda**, al abrirse la venta los clientes ingresan a una **fila virtual**: el sistema les informa su posición y los habilita a comprar en orden, por tandas.
7. Un cliente puede **solicitar la devolución** de una entrada hasta 72 horas antes del evento. Un administrador aprueba o rechaza la solicitud; si la aprueba, el asiento o lugar en la zona se libera y se solicita el reintegro del dinero a la pasarela de pagos. Si un evento se **cancela**, todas sus entradas vendidas pasan a reintegro y se notifica a los compradores.
8. En el ingreso al evento, el personal de acceso escanea el QR. El sistema valida que la entrada exista, corresponda a ese evento y no haya sido usada, y la marca como utilizada (un QR no puede ingresar dos veces).
9. El sistema debe permitir **búsquedas por múltiples criterios**: tipo de evento, organizador, participante, ciudad y rango de fechas, con ordenamiento por fecha, precio o relevancia.
10. El sistema debe registrar las **preferencias** de los clientes (tipos de evento, artistas favoritos, ciudad) y **sugerir eventos** en función de su historial (similar a “quienes compraron entradas para este evento también compraron para…”).
11. Enviar **notificaciones** (email o push) al confirmar una compra, ante la cancelación o cambio de un evento, y cuando un artista favorito de un cliente publica un evento en su ciudad.
12. Los organizadores deben poder consultar **reportes** de sus eventos: entradas vendidas y devueltas por sector, porcentaje de ocupación y recaudación.

## Consideraciones de arquitectura y no funcionales

- Utilizar **arquitectura de microservicios**, implementando cada microservicio con **Spring Boot** como un proyecto independiente, que pueda construirse y ejecutarse por separado.
- **Punto de entrada único**: los clientes externos acceden al sistema a través de un API Gateway.
- **Comunicación**: utilizar APIs REST, mayormente síncronas, aunque podría utilizarse comunicación asíncrona (fire-and-forget o mensajería) donde se justifique, por ejemplo para notificaciones o para la fila virtual.
- **Persistencia**: cada microservicio debe tener preferentemente su propia base de datos (puede ser la misma tecnología, pero esquemas separados). Al menos un microservicio debe utilizar una **base de datos NoSQL** (por ej. MongoDB para el catálogo de eventos o las preferencias de los clientes), justificando la elección. Se valorará el uso de otras tecnologías según el tipo de datos (por ej. Redis para reservas temporales o la fila virtual).
- **Concurrencia e idempotencia**: el mecanismo que impide la doble venta debe funcionar aun con **varias instancias** del mismo microservicio ejecutándose a la vez. Debe explicarse y probarse el mecanismo elegido (bloqueos, control de versiones, restricciones de unicidad, claves de idempotencia, etc.).
- **Consistencia entre servicios**: una compra involucra a más de un microservicio (disponibilidad de lugares, pagos, emisión de entradas). Dado que no existe una única transacción de base de datos que los abarque, se debe definir cómo se mantiene la consistencia y cómo se deshacen los pasos ya realizados si uno falla (por ej., vía compensaciones).
- **Resiliencia**: implementar reintentos, timeouts y fallback ante fallos de servicios externos (pasarela de pagos, servicio de notificaciones).
- **Cache**: definir qué datos se cachean (por ej. catálogo y detalle de eventos, resultados de búsqueda), dónde, con qué tiempo de vida (TTL) y cómo se invalidan, y justificar qué datos **no** deben cachearse (p. ej. la disponibilidad de asientos).
- **Trazabilidad**: cada solicitud HTTP debe quedar registrada en un log con el endpoint, el usuario, el código de respuesta y el tiempo de procesamiento. Cada operación crítica (compra, devolución, cancelación, ingreso al evento) debe quedar registrada en un log centralizado, con un identificador que permita seguir una misma operación a través de los distintos microservicios.
- **Seguridad**: autenticación y autorización con JWT (usuarios y roles: cliente, organizador, administrador y personal de acceso).
- **Despliegue**: cada microservicio (y cada base de datos, el gateway, etc.) debe poder desplegarse en su **propio contenedor Docker**, y todo el sistema debe poder levantarse con un único `docker-compose.yml`.
- **Rendimiento**: el sistema debe soportar la apertura de venta de un evento masivo. Se debe realizar al menos una prueba de carga sobre el flujo de compra (por ej. con JMeter) y reportar sus resultados.

## Metodología sugerida

Se sugiere seguir prácticas de **desarrollo guiado por especificaciones** (*spec-driven development*), por ejemplo utilizando **OpenSpec**. La idea es que cada funcionalidad o cambio comience con una especificación escrita y revisada por el grupo (propuesta → especificación de requisitos y escenarios → diseño → tareas) antes de implementarse, y que esas especificaciones queden versionadas en el repositorio junto al código. Las especificaciones sirven además como base para los contratos OpenAPI y para los casos de prueba. Si se utilizan coding agents de IA para programar, las especificaciones son el insumo que debe guiar a estos agentes.

## Interfaz gráfica

El foco del trabajo es el backend. Sin embargo, **se valorará positivamente** que el sistema no sea solo backend y cuente con una **interfaz gráfica** (p. ej. una aplicación web en React, Angular, Vue o Thymeleaf) que consuma la API a través del gateway. Como mínimo, sería deseable cubrir los flujos principales del cliente: buscar eventos, elegir sector y asientos, comprar y ver sus entradas.

## Modalidad de entrega

El trabajo se realiza en grupo y se entrega en **dos partes incrementales**, sobre el mismo repositorio. La primera entrega se focaliza en el **núcleo del sistema** (el circuito de publicación, búsqueda y compra sin doble venta); la segunda completa la funcionalidad y los aspectos no funcionales restantes. Los números entre paréntesis refieren a la sección [Funcionalidad](#funcionalidad).

| | Entrega 1: Core del sistema | Entrega 2: Sistema completo |
|---|---|---|
| **Funcionalidad** | Eventos y lugares (1, 2), publicación (3), compra con reserva temporal y emisión de QR (4), no doble venta (5), devoluciones y cancelación de eventos (7), búsqueda por múltiples criterios (9). | Fila virtual (6), control de acceso con QR (8), preferencias y recomendaciones (10), notificaciones (11), reportes para organizadores (12). |
| **Arquitectura** | Microservicios Spring Boot con API REST síncrona, API Gateway, una base relacional por microservicio, mecanismo contra la doble venta. Pasarela de pagos simulada (mock). | Base NoSQL (MongoDB) en al menos un servicio, consistencia entre servicios y compensaciones, resiliencia, cache, bitácora y log centralizado, comunicación asíncrona donde se justifique. |
| **Seguridad** | Sin autenticación (el usuario se identifica por parámetro o encabezado). | Autenticación y autorización con JWT y roles. |
| **Despliegue** | Ejecución local de los microservicios (las bases de datos pueden levantarse con Docker). | Cada microservicio en un contenedor Docker separado; todo el sistema con un docker-compose.yml. |
| **Testing** | Pruebas unitarias de la lógica de negocio y una prueba que verifique la no doble venta. | Pruebas de integración entre servicios, pruebas de seguridad de endpoints y prueba de carga. |

## Tareas: Entrega 1 (core)

- **Features y User Stories:**
  - Transformar los requerimientos dados en una serie de features y user stories (de todo el sistema), incluyendo criterios de aceptación para las historias.
- **Arquitectura de microservicios:**
  - Definir los microservicios, sus responsabilidades y API pública.
  - Diagrama de módulos de la arquitectura de solución.
  - Diagrama de secuencia del flujo de compra de una entrada numerada, incluyendo qué ocurre si el pago es rechazado o la reserva vence.
- **Modelo de datos:**
  - Diagrama entidad-relación por microservicio, indicando claves y relaciones entre servicios. Se debe indicar también si existen entidades solapadas entre bases.
- **Diseño de API REST:**
  - Listado de endpoints con método, ruta, parámetros, códigos de respuesta, ejemplos de request/response (JSON).
  - Documentar utilizando OpenAPI (Swagger), incluyendo descripciones, ejemplos y códigos de error.
- **Implementación:**
  - Desarrollar en Spring Boot los microservicios y el API Gateway necesarios para la funcionalidad de esta entrega, en forma completa.
  - Incluir mínimamente un README de cómo ejecutar, pero también documentación técnica adicional.
- **Testing:**
  - Pruebas unitarias de la lógica de negocio (compra, devoluciones, búsqueda).
  - Una prueba que simule muchas compras simultáneas del mismo asiento (o del último lugar de una zona) y verifique que solo una resulta exitosa.

**Entregable 1** (fecha sin definir en el enunciado): repositorio Git con código fuente, especificaciones (si se utilizó OpenSpec u otra práctica similar), documentación de diseño, README con instrucciones de ejecución, datos de prueba y una colección de requests (Postman o similar) para probar los endpoints.

## Tareas: Entrega 2 (sistema completo)

- **Features y User Stories:**
  - Completar los criterios de aceptación de las historias restantes y actualizar las que hayan cambiado.
- **Arquitectura y modelo de datos:**
  - Actualizar los diagramas de módulos y entidad-relación con los nuevos servicios y la base NoSQL.
  - Estrategias de consistencia (eventual vs. fuerte) y manejo de fallos parciales en la compra (compensaciones).
- **Decisiones tecnológicas:**
  - Justificar elección de las tecnologías (en particular, la base NoSQL elegida y para qué datos).
  - Justificar uso de patrones: API Gateway, Circuit Breaker, Backend For Frontend, Saga, Service Discovery, etc.
- **Implementación:**
  - Desarrollar la funcionalidad restante. La fila virtual y las recomendaciones pueden implementarse con una versión simplificada, justificando las simplificaciones.
  - Incorporar JWT con roles, la base NoSQL, la estrategia de cache, la resiliencia ante servicios externos y la bitácora/log centralizado.
  - Empaquetar cada microservicio en un contenedor Docker separado y orquestar el sistema completo con docker-compose.
- **Diseño de API REST:**
  - Actualizar la documentación OpenAPI con los nuevos endpoints y los requisitos de seguridad.
- **Testing:**
  - Pruebas de integración entre microservicios (p. ej. con Testcontainers o levantando el entorno con docker-compose).
  - Pruebas que verifiquen que cada endpoint solo es accesible por los roles correspondientes.
  - Prueba de carga del flujo de compra, con un breve informe de resultados (usuarios concurrentes, tiempos de respuesta, errores).

**Entregable 2** (fecha sin definir en el enunciado): repositorio Git actualizado con código fuente, archivo docker-compose.yml para levantar todos los servicios (bases de datos, gateway, microservicios, etc.), especificaciones y documentación de diseño actualizadas, README con instrucciones de ejecución y descripción del diseño, colección de requests actualizada e informe de la prueba de carga. Si se desarrolló interfaz gráfica, incluir instrucciones para ejecutarla.
