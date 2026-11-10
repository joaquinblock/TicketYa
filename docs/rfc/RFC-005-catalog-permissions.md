# RFC-005: Quién administra el catálogo (lugares y participantes)

- **Estado:** Propuesta, a decidir por el equipo antes de la Épica 9 (JWT y roles)
- **Relacionado:** [`permissions.md`](../architecture/permissions.md) (sección *Pendiente*), [`eventservice.md`](../architecture/eventservice.md), PR #103 (ABM de participantes), RFC-001 (modalidad de autenticación)

## Contexto

Desde el PR #100, cada endpoint del event-service declara el permiso que necesita con `@RequiresPermission`, y `PermissionInterceptor` lo verifica contra el auth-service. Hoy hay dos endpoints protegidos, los dos con `EVENT_MANAGE`:

- `POST /api/events`
- `PUT /api/events/{id}/participants`

El **catálogo** quedó sin proteger: cualquiera puede crear, modificar o borrar lugares, sectores, asientos y participantes.

| Recurso | Endpoints de escritura | Permiso hoy |
|---|---|---|
| Lugares, sectores y asientos | `POST`/`PUT`/`DELETE` en `/api/venues/**` | Ninguno |
| Participantes | `POST`/`PUT`/`DELETE` en `/api/participants/**` | Ninguno |

**Ningún permiso cargado en `AuthSeeder` cubre el catálogo.** `EVENT_MANAGE` es *"crear, editar y publicar eventos propios"*, y `EVENT_ADMIN` es *"suspender o cancelar cualquier evento"*.

## Qué dice la consigna

**No dice quién administra el catálogo.** Las pistas que da:

| Dónde | Qué dice |
|---|---|
| Tipos de usuario | **Administradores:** *"gestión completa, mantenimiento y tareas operativas"*. **Organizadores:** *"diseñan y publican eventos"* |
| Punto 1 | Los participantes (artistas, equipos y disertantes) son un dato del evento. No dice quién los da de alta |
| Punto 2 | Describe qué tiene un lugar y sus sectores. No dice quién los crea |
| Punto 3 | Los organizadores *"configuran sectores y precios"*: eligen qué sectores del lugar venden y a qué precio (`event_sector`). No crean los sectores del lugar |
| Modalidad, Entrega 1 | *"Sin autenticación"*: dejar el catálogo sin permiso **cumple** la Entrega 1 |
| Tareas, Entrega 2 | *"Pruebas que verifiquen que cada endpoint solo es accesible por los roles correspondientes"*: para la Entrega 2 **hay que decidirlo** |

## Problema

Un lugar o un participante no es de una organización: lo **comparten** todas.

- **El Estadio del Sur lo usan muchas organizaciones.** Si cualquier organizador pudiera editarlo o borrar un sector, afectaría los eventos de otras.
- **Un artista toca para varias productoras,** y los clientes lo van a marcar como favorito (Entrega 2). Si un organizador lo renombra, cambia lo que ven todos los eventos.
- **Pero un organizador que arma un festival necesita poder dar de alta a un artista nuevo.** Si solo el admin puede hacerlo, el organizador depende de que alguien le cargue el artista antes de armar la grilla.

## Opciones

### A. Todo el catálogo, solo admin

Permiso nuevo `CATALOG_MANAGE`, solo para `ADMIN`. Se aplica a todos los `POST`/`PUT`/`DELETE` de `/api/venues/**` y `/api/participants/**`.

- **A favor:** es lo más simple y es lo que dice la consigna (*"gestión completa"*). Ningún organizador puede romper datos que usan otros.
- **En contra:** el organizador no puede dar de alta un artista que falta. Tiene que pedírselo al admin.

### B. Lugares solo admin, participantes también organizadores

- **Lugares:** `CATALOG_MANAGE` (admin).
- **Participantes:** se pide `CATALOG_MANAGE` **o** `EVENT_MANAGE`.

`@RequiresPermission` hoy acepta un solo permiso, así que habría que extenderlo a una lista (*cualquiera de estos*).

- **A favor:** el organizador arma su grilla solo, y los lugares quedan protegidos.
- **En contra:** un organizador puede renombrar o borrar un participante que usan otros. El borrado ya está limitado: da `409 participant_in_use` si está en algún evento. El renombre no.

### C. Como B, pero el organizador solo crea

- **Crear un participante:** `CATALOG_MANAGE` o `EVENT_MANAGE`.
- **Modificar y borrar:** solo `CATALOG_MANAGE`.

- **A favor:** el organizador puede sumar artistas, pero no tocar los que ya existen. Corregir un nombre mal cargado queda para el admin.
- **En contra:** es la opción con más reglas para explicar y testear.

### D. Dueño por organización

Cada lugar y cada participante tiene `organization_id`, y solo esa organización lo edita.

- **A favor:** cada uno administra lo suyo.
- **En contra:** **contradice el modelo.** Los lugares y los artistas se comparten. Un mismo artista quedaría cargado dos veces, una por productora, y se partirían sus favoritos, que es justo lo que evita el UNIQUE `(name, type)` de `participant`.

## Comparación

| | A. Solo admin | B. Participantes también org. | C. Org. solo crea | D. Dueño por org. |
|---|---|---|---|---|
| Lugares | Admin | Admin | Admin | Su organización |
| Crear participante | Admin | Admin u organizador | Admin u organizador | Su organización |
| Modificar o borrar participante | Admin | Admin u organizador | Admin | Su organización |
| El organizador arma su grilla solo | No | Sí | Sí | Sí |
| Un organizador puede afectar a otros | No | Sí (renombrar) | No | No |
| Cambios en `@RequiresPermission` | No | Aceptar varios permisos | Aceptar varios permisos | Además, chequeo de dueño en cada service |
| Encaja con el modelo actual | Sí | Sí | Sí | No |

## Cambios que lleva cualquier opción

1. `AuthSeeder`: el permiso `CATALOG_MANAGE` (*"Administrar lugares, sectores, asientos y participantes"*), asignado a `ADMIN`.
2. `org.group10.event.security.Permission`: la constante `CATALOG_MANAGE`.
3. `@RequiresPermission` en los métodos de escritura de `VenueController`, `SectorController`, `SeatController` y `ParticipantController`.
4. Tests de controller: mockear `getPermissions` con el permiso que corresponda, y los casos de `403`.
5. Postman: los requests de escritura pasan a llevar `X-User-Id: 3` (el admin del seeder).
6. `permissions.md`: la tabla de *Endpoints protegidos* y la matriz de permisos por rol.

**Las lecturas (`GET`) quedan sin permiso,** porque el catálogo es público: lo consultan los clientes para buscar eventos.

## Recomendación

**C**, o **A** si se quiere lo más simple.

- **C** resuelve el caso del organizador que necesita un artista nuevo, sin que pueda modificar datos que comparten otras organizaciones.
- **A** es suficiente si el equipo acepta que el admin carga los artistas. Además, es la lectura más literal de la consigna.
- **D** queda descartada, porque contradice el modelo: lugares y participantes son compartidos.

## Preguntas abiertas

- ¿El organizador tiene que poder dar de alta artistas, o los carga el admin? Esto decide entre A y C.
- ¿Hace falta que un organizador pueda **proponer** un lugar nuevo, por ejemplo un club de barrio que no está cargado? Si es así, puede crearse en un estado *pendiente de aprobación*. Lo dejaría para la Entrega 2.
- ¿Se aplica en la Entrega 1, o recién con JWT? Con el interceptor del PR #100 ya se puede aplicar hoy: solo cambia el header que hay que mandar.
