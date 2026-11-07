# Issues de TicketYa

Issues del repositorio [`joaquinblock/TicketYa`](https://github.com/joaquinblock/TicketYa), con su estado, enlace y contenido.

## #15 — Manejo de errores común

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/15

```md
Parte de #1.

## Qué incluye
- `ErrorDto` con formato único de error para todos los servicios.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) y excepciones de negocio base, reutilizando el del Integrador3.
- Errores documentados en OpenAPI.

## Criterios de aceptación
- [ ] Validaciones, recursos inexistentes y errores de negocio responden con el mismo formato y el status HTTP correcto.
```

## #14 — Esqueleto del resto de los microservicios

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/14

```md
Parte de #1. Depende de la definición de microservicios.

## Qué incluye
- Un módulo por microservicio heredando del POM padre, con su `application.yml`, `compose.yaml` (base propia) y clase main.
- Ruta de cada servicio en el gateway y su entrada en el Swagger unificado.

## Criterios de aceptación
- [ ] Cada servicio arranca por separado con su base.
- [ ] El gateway enruta a todos los servicios.
```

## #13 — Definir los microservicios y sus responsabilidades

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/13

```md
Parte de #1.

## Qué incluye
- Lista de microservicios, responsabilidad de cada uno y qué tablas del DER le pertenecen.
- API pública a alto nivel de cada servicio y comunicación entre ellos.
- Diagrama de módulos de la arquitectura.

## Criterios de aceptación
- [ ] Documento en `docs/` revisado por el grupo.
- [ ] Cada tabla del DER está asignada a un único servicio (o se documenta el solapamiento).
```

## #11 — Épica 11: Despliegue con Docker y pruebas de integración y carga

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/11

```md
## Objetivo
Empaquetar y probar el sistema completo.

## Alcance
- Dockerfile por microservicio.
- `docker-compose.yml` único que levanta bases, gateway y microservicios.
- Pruebas de integración entre microservicios (Testcontainers o docker-compose).
- Prueba de carga del flujo de compra (JMeter) con informe: usuarios concurrentes, tiempos de respuesta y errores.

## Criterios de aceptación
- [ ] `docker compose up` levanta todo el sistema.
- [ ] Informe de la prueba de carga en el repo.
```

## #10 — Épica 10: No funcionales — consistencia, resiliencia, caché y trazabilidad

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/10

```md
## Objetivo
Consideraciones de arquitectura y no funcionales del enunciado.

## Alcance
- Consistencia entre servicios en la compra (Saga con compensaciones); estrategia eventual vs. fuerte.
- Resiliencia: reintentos, timeouts, circuit breaker y fallback ante la pasarela de pagos y el servicio de notificaciones.
- Caché: qué se cachea, dónde, con qué TTL y cómo se invalida (y qué no, como la disponibilidad de asientos).
- Trazabilidad: log de cada request (endpoint, usuario, código, tiempo) y log centralizado de operaciones críticas con un ID que las siga entre servicios.
- Comunicación asíncrona donde se justifique.
- Justificación de patrones (API Gateway, Circuit Breaker, BFF, Saga, Service Discovery).

## Criterios de aceptación
- [ ] Una compra con pago fallido deshace los pasos anteriores.
- [ ] Se puede seguir una operación de punta a punta en el log centralizado.
```

## #9 — Épica 9: Seguridad — autenticación y autorización con JWT

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/9

```md
## Objetivo
Autenticación y autorización con JWT y roles: cliente, organizador, administrador y personal de acceso.

## Alcance
- Emisión/validación de JWT (proveedor de identidad).
- Validación en el gateway y/o en cada microservicio.
- Permisos por rol según las tablas `role`, `permission` y `role_permission`.
- Requisitos de seguridad en la documentación OpenAPI.

## Criterios de aceptación
- [ ] Pruebas que verifican que cada endpoint solo es accesible por los roles correspondientes.
```

## #8 — Épica 8: Preferencias, recomendaciones, notificaciones y reportes

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/8

```md
## Objetivo
Funcionalidades 10, 11 y 12 del enunciado.

## Alcance
- Preferencias del cliente (tipos de evento, artistas favoritos, ciudad) en base NoSQL.
- Recomendaciones según historial ("quienes compraron esto también compraron…"), versión simplificada justificada.
- Notificaciones (email/push) al confirmar compra, ante cancelación o cambio de evento, y cuando un artista favorito publica en la ciudad del cliente.
- Reportes para organizadores: vendidas y devueltas por sector, ocupación y recaudación.

## Criterios de aceptación
- [ ] Al menos un microservicio usa NoSQL, con la elección justificada.
- [ ] Las notificaciones se envían en forma asíncrona.
```

## #7 — Épica 7: Fila virtual y control de acceso con QR

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/7

```md
## Objetivo
Funcionalidades 6 y 8 del enunciado.

## Alcance
- Fila virtual para eventos de alta demanda: posición del cliente y habilitación por tandas (puede ser simplificada, justificando la simplificación).
- Validación del QR en el ingreso: que la entrada exista, sea de ese evento y no haya sido usada; marcarla como utilizada.

## Criterios de aceptación
- [ ] Los clientes de un evento de alta demanda compran en orden de llegada.
- [ ] Un QR no puede ingresar dos veces.
```

## #6 — Épica 6: Documentación y entregables

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/6

```md
## Objetivo
Todo lo que se entrega además del código.

## Alcance
- Features y user stories de todo el sistema, con criterios de aceptación.
- Definición de microservicios, responsabilidades y API pública.
- Diagrama de módulos, diagrama de secuencia de compra y DER por microservicio.
- Listado de endpoints y documentación OpenAPI (descripciones, ejemplos, códigos de error).
- README con instrucciones de ejecución, datos de prueba y colección de Postman.
- Especificaciones (spec-driven development / OpenSpec) versionadas en el repo.

## Criterios de aceptación
- [ ] Entregable 1 completo según el enunciado.
- [ ] Documentación actualizada para el Entregable 2.
```

## #5 — Épica 5: Devoluciones y cancelación de eventos

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/5

```md
## Objetivo
Funcionalidad 7 del enunciado.

## Alcance
- Solicitud de devolución hasta 72 h antes del evento.
- Aprobación o rechazo por un administrador; si se aprueba, se libera el lugar y se pide el reintegro a la pasarela.
- Cancelación de un evento: todas sus entradas pasan a reintegro.

## Criterios de aceptación
- [ ] No se puede pedir devolución a menos de 72 h del evento.
- [ ] Una devolución aprobada libera el asiento o el lugar en la zona.
- [ ] Pruebas unitarias de la lógica de devoluciones.
```

## #4 — Épica 4: Compra — reserva temporal, pago, emisión de QR y no doble venta

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/4

```md
## Objetivo
Funcionalidades 4 y 5 del enunciado: el núcleo del sistema.

## Alcance
- Reserva temporal de asientos/lugares (p. ej. 10 minutos) y liberación al vencer o si el pago es rechazado.
- Pasarela de pagos simulada (mock).
- Emisión de entradas con código QR único.
- Límite de entradas por cliente y evento.
- Mecanismo contra la doble venta que funcione con varias instancias (bloqueo optimista, restricciones de unicidad, claves de idempotencia).

## Criterios de aceptación
- [ ] Diagrama de secuencia de la compra de una entrada numerada (incluye pago rechazado y reserva vencida).
- [ ] Prueba que simula muchas compras simultáneas del mismo asiento (o último lugar de una zona) y verifica que solo una es exitosa.
- [ ] Reintentar un pago con la misma clave de idempotencia no genera una segunda compra.
```

## #3 — Épica 3: Catálogo — eventos, lugares, sectores, publicación y búsqueda

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/3

```md
## Objetivo
Funcionalidades 1, 2, 3 y 9 del enunciado.

## Alcance
- ABM de lugares con sectores numerados (filas/asientos) y no numerados (capacidad).
- Eventos en estado borrador, configuración de sectores y precios, publicación.
- Suspensión y cancelación de eventos por parte de administradores.
- Búsqueda por tipo, organizador, participante, ciudad y rango de fechas, ordenada por fecha, precio o relevancia.

## Criterios de aceptación
- [ ] Un organizador puede crear, configurar y publicar un evento.
- [ ] La búsqueda combina criterios y ordenamientos.
- [ ] Pruebas unitarias de la búsqueda.
```

## #2 — Épica 2: Modelo de datos — entidades de todos los microservicios

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/2

```md
## Objetivo
Traducir el diagrama entidad-relación (`ticketya_esquema.drawio`) a entidades JPA en el microservicio que corresponde a cada una, todo en inglés.

## Alcance
- Entidades, enums y repositorios por microservicio.
- Relaciones entre servicios por ID (sin FK física entre bases distintas).
- Seeder por servicio con datos de prueba (`ddl-auto: create` mientras el modelo cambia).
- Identificar y documentar entidades solapadas entre bases.

## Criterios de aceptación
- [ ] Todas las tablas del diagrama tienen su entidad en el servicio correspondiente.
- [ ] Cada servicio arranca, crea sus tablas y carga sus datos de prueba.
- [ ] DER por microservicio actualizado en el repo.
```

## #1 — Épica 1: Base de microservicios — estructura, dependencias y API Gateway

- Estado: abierta
- URL: https://github.com/joaquinblock/TicketYa/issues/1

```md
## Objetivo
Dejar armada la base sobre la que se construyen todos los microservicios: estructura del repo, dependencias comunes, API Gateway y forma de ejecución local.

## Alcance
- POM padre `org.group10` con dependencias comunes (webmvc, validation, actuator, springdoc, Lombok, docker-compose, devtools, test).
- Un módulo Spring Boot independiente por microservicio, cada uno con su `compose.yaml` y base propia.
- API Gateway (Spring Cloud Gateway WebMVC) como punto de entrada único, con Swagger unificado.
- Manejo de errores común.

## Criterios de aceptación
- [ ] `./mvnw clean install` compila todos los módulos.
- [ ] Cada microservicio arranca por separado y levanta su base con Docker automáticamente.
- [ ] Todas las rutas públicas pasan por el gateway (puerto 8080).
- [ ] El Swagger del gateway muestra la API de cada servicio.
```

> Nota: la lista consultada contiene 14 issues (#1–#11 y #13–#15); no incluye una issue #12.