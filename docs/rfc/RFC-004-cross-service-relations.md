# RFC-004: Relaciones entre tablas de distintos microservicios

- **Estado:** Abierto: a decidir en equipo antes de implementar el CRUD de eventos (Épica 3, #3)
- **Relacionado:** `event-service.md` (columnas `organization_id` y `created_by`), `microservices.md` (Clients síncronos y mensajería), RFC-002 (contratos compartidos)

## Contexto

`Organization` y `Organizer` se modelaron en el **auth-service**:

| Tabla | Campos | Relaciones |
|---|---|---|
| `organization` | `legal_name`, `display_name`, `type` (`COMPANY`, `CIVIL_ASSOCIATION`), `tax_id`, `bank_account_number`, todos `NOT NULL` | — |
| `organizer` | `user_id` (PK), `organization_id`, `fecha_baja` (nullable) | `user_id` 1 a 1 con `user.id`; `organization_id` N a 1 con `organization.id` |

`event` vive en el **event-service**, en otra base, y tiene que relacionarse con las dos:

- **`event.organization_id`:** a nombre de qué organización se publica el evento.
- **`event.created_by`:** qué organizador lo creó.

Hoy `Event` guarda los dos campos como `Long`, sin relación JPA. En el diseño de tablas, `event.organization_id` aparece como FK N a 1 hacia `organization.id`, y la idea era mapearla en `Event` con `@ManyToOne`.

## Problema

**Una FK o un `@ManyToOne` solo funciona dentro de una misma base.** Cada microservicio tiene la suya y JPA solo conoce las entidades de su servicio, así que `Event` no puede tener `@ManyToOne Organization` si `Organization` es del auth-service: el event-service ni siquiera arranca.

Al separar las bases se pierden tres cosas que la base daba gratis, y hay que decidir cómo reemplazar cada una:

| Se pierde | Qué garantizaba | Pregunta a resolver |
|---|---|---|
| **FK** | Que `organization_id` apunte a una organización que existe | ¿Quién valida que la organización y el organizador existen, y cuándo? |
| **JOIN** | Leer el evento junto con el nombre de su organización | ¿Cómo se muestra `display_name` en las páginas de eventos? |
| **`ON DELETE`** | Que no se borre una organización con eventos | ¿Qué pasa con los eventos si auth da de baja una organización o un organizador? |

La transacción entre las dos bases también se pierde, pero no afecta a este caso: crear un evento solo escribe en el event-service.

## Opciones

### A. Referencia por id (FK lógica)

`Event` guarda solo el id, sin constraint ni relación JPA (lo que hay hoy):

```java
@Column(nullable = false)
private Long organizationId;   // organization.id en auth-service, sin FK
```

- **Reemplazo de la FK:** al crear el evento, el event-service consulta a auth con `AuthClient` (`GET /api/organizers/{userId}`) y obtiene el `organizationId` del organizador. Si da 404, no se crea.
- **Reemplazo del `ON DELETE`:** auth nunca borra físicamente: da de baja (`fecha_baja`). Así ningún id queda huérfano.
- **Reemplazo del JOIN:** combinarla con la opción B o la D.

### B. Composición en la API

Al leer un evento, el event-service pide los datos de la organización a auth y arma la respuesta:

```
GET /api/events/5
  → event-service lee event (organization_id = 1)
  → AuthClient: GET /api/organizations/1 → { displayName: "Del Sur Producciones" }
  → responde el evento con organizationName
```

- **Listados:** necesitan un endpoint por lote en auth (`GET /api/organizations?ids=1,2,3`) para no hacer una llamada por evento (N+1 entre servicios).
- **Disponibilidad:** si auth está caído, la lectura falla o se degrada (el evento sin nombre de organización).

### C. Réplica local en el event-service

El event-service mantiene **su propia tabla `organization`**: una copia de solo lectura con los campos que necesita. Sobre esa copia sí se puede poner `@ManyToOne` y una FK real, porque está en la misma base:

```java
// event-service: copia de solo lectura; el dueño del dato sigue siendo auth
@Entity
@Table(name = "organization")
public class OrganizationReplica {
    @Id
    private Long id;              // el mismo id que en auth, no se genera acá
    private String displayName;
}

// Event
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "organization_id", nullable = false)
private OrganizationReplica organization;
```

**Cómo se mantiene al día:**

| Mecanismo | Cuándo |
|---|---|
| Al crear un evento, si la organización no está en la réplica, se trae de auth y se guarda | Entrega 1, sin mensajería |
| auth publica `OrganizationCreated` / `OrganizationUpdated` y el event-service actualiza su copia | Entrega 2, con RabbitMQ |

**Reglas:** solo auth escribe el dato real, la réplica no tiene ABM propio, y la copia tiene **consistencia eventual** (puede estar unos segundos desactualizada).

### D. Snapshot en la fila

Se copia el dato en la propia tabla `event` al crearla, por ejemplo `event.organization_name`. No hay tabla réplica ni relación. Sirve para datos que casi no cambian o que conviene congelar, como hace `ticket.price` con `event_sector.price`. `microservices.md` ya lo menciona para el nombre de la organización.

### E. Mover `organization` al event-service

`Organization` pasa a ser del event-service, con `@ManyToOne` y FK reales desde `event`. En auth queda `organizer.organization_id` como `Long`, sin FK. **Así el problema pasa a la relación `organizer` → `organization`.** Además, `tax_id` y `bank_account_number` quedarían en el catálogo, que es el servicio más expuesto y el que más se cachea.

## Comparación

| | A. Id | B. Composición | C. Réplica | D. Snapshot | E. Mover |
|---|---|---|---|---|---|
| `@ManyToOne` en `Event` | No | No | **Sí**, a la réplica | No | Sí |
| Valida que exista al crear | Llamada a auth | Llamada a auth | Llamada a auth o réplica | Llamada a auth | FK local |
| Mostrar `display_name` | Necesita B o D | Llamada a auth en cada lectura | JOIN local | Columna local | JOIN local |
| Lecturas si auth está caído | — | Fallan o se degradan | Funcionan | Funcionan | Funcionan |
| Dato desactualizado | No aplica | Nunca | Eventual | Congelado | Nunca |
| Costo en Entrega 1 | Bajo (casi hecho) | Bajo | Medio (tabla + sincronización) | Bajo | Medio, y rehace el modelo de auth |
| Encaja con `microservices.md` | Sí | Sí | Sí, con la mensajería de Entrega 2 | Sí (ya la menciona) | No: la organización es identidad, no catálogo |

Las opciones se pueden combinar, por ejemplo A + D o A + C. A es la base de casi todas: siempre hace falta la validación al crear y la baja lógica en auth.

## Preguntas abiertas

- ¿El mapeo con `@ManyToOne` en `Event` es un requisito, o alcanza con que la relación esté documentada y validada?
- Si un evento se publicó con un nombre de organización y la organización lo cambia, ¿el evento debe mostrar el nombre nuevo (C) o el que tenía al crearse (D)?
- ¿`fecha_baja` en `organizer` bloquea solo crear eventos nuevos, o también editar o publicar los que ya creó?
- ¿Hace falta un baja lógica también en `organization`? Hoy no tiene columna para eso.
- ¿`created_by` pasa a llamarse `organizer_id` para que diga a qué apunta?

## Decisión

Pendiente. Cuando se decida, registrarla como ADR y actualizar `event-service.md` (diagrama y columnas de `event`) y `microservices.md`.
