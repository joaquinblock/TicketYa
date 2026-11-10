# Nombres de las user stories

> Cómo se nombran las user stories, tanto en las issues de GitHub como en [`user-stories.md`](../user-stories.md). El formato ya lo usan las historias de ticket-service (`US-TKT-01` a `US-TKT-28`). Las de event-service pasaron a `US-EVT` el 2026-11-09.

## Formato

```
US-<SERVICIO>-<NN>: <Título>
```

| Parte | Regla | Ejemplo |
|---|---|---|
| `US` | Fijo: indica que es una user story | `US` |
| `<SERVICIO>` | Siglas del **servicio responsable**, en mayúsculas (ver la tabla de abajo) | `TKT` |
| `<NN>` | Número de dos dígitos, correlativo **dentro de cada servicio**. Nunca se reutiliza, aunque la historia se descarte | `02` |
| `: ` | Dos puntos y un espacio, siempre igual (no `-`, no solo un espacio) | `: ` |
| `<Título>` | Corto, en infinitivo y sin punto final. Dice qué se logra, no cómo se implementa | `Consultar la disponibilidad` |

Ejemplos: `US-TKT-02: Consultar la disponibilidad`, `US-EVT-02: Configurar y publicar eventos en borrador`.

## Siglas por servicio

| Servicio | Siglas | Estado |
|---|---|---|
| ticket-service | `TKT` | En uso (US-TKT-01 a 28) |
| event-service | `EVT` | En uso (US-EVT-01 a 05) |
| payment-service | `PAY` | En uso (US-PAY-01 a 15) |
| auth-service | `AUTH` | Propuesto |
| queue-service | `QUE` | Propuesto |
| notification-service | `NOT` | Propuesto |
| recommendation-service | `REC` | Propuesto |

Las siglas propuestas se confirman al cargar la primera historia de ese servicio.

## Qué lleva el prefijo y qué no

- **Lleva `US-`:** una historia con un actor que obtiene algo de valor (cliente, organizador, administrador o personal de acceso). También las de `Tipo: técnica` de `user-stories.md`, porque sirven a una historia de negocio. Por ejemplo, `US-TKT-25: Exponer el historial de compras`.
- **No lleva `US-`:** las tareas del equipo que no son historias, como armar entidades, la estructura del proyecto, seeders o tests. Llevan un título descriptivo sin número, como las issues #12 a #25 (por ejemplo, `Entidades de auth-service: organizaciones y personal`).
- **Las épicas** siguen con su propio formato: `Épica N: <tema>`.

## Issue de GitHub

- **El título de la issue y el de `user-stories.md` son el mismo.** Si cambia uno, se cambia el otro.
- **La issue va dentro de su épica,** aunque la épica sea de otro servicio. Por ejemplo, `US-TKT-01` es de ticket-service pero vive en la Épica 3 (#3), porque el inventario se crea al publicar un evento.
- **El cuerpo** sigue esta plantilla:

```markdown
Parte de #<épica>.

Historia y criterios de aceptación: [US-XXX-NN](https://github.com/joaquinblock/TicketYa/blob/main/docs/user-stories.md#<ancla>)

Decisiones de diseño: `docs/architecture/<servicio>.md`.
```

## Renombre de las issues de la Épica 3

Antes se numeraban según la épica (`3.1`, `3.2`, …), con separadores mezclados (espacio, `:` y `-`):

| Issue | Antes | Ahora |
|---|---|---|
| #39 | 3.1 ABM de lugares con sectores numerados (filas/asientos) y no numerados (capacidad). | US-EVT-01: Administrar lugares con sectores numerados y no numerados |
| #40 | 3.2 Eventos en estado borrador, configuración de sectores y precios, publicación. | US-EVT-02: Configurar y publicar eventos en borrador |
| #41 | 3.3 Suspensión y cancelación de eventos por parte de administradores. | US-EVT-03: Suspender y cancelar eventos como administrador |
| #42 | 3.4: Crear el inventario al publicar un evento | US-TKT-01: Crear el inventario al publicar un evento |
| #43 | 3.5 Búsqueda por tipo, organizador, participante, ciudad y rango de fechas, ordenada por fecha, precio o relevancia. | US-EVT-04: Buscar eventos con filtros y ordenamiento |
| #56 | 3.6 - Generar Seeder y correr tests | US-EVT-05: Generar el seeder y correr los tests |

## Pendientes

- [x] **Escribir las US-EVT en `user-stories.md`:** ya están; antes las issues #39, #40, #41, #43 y #56 no tienen historia ni criterios de aceptación a los que enlazar
- [ ] **Pasar el detalle de la #43 al cuerpo:** el título viejo listaba los filtros (tipo, organizador, participante, ciudad, rango de fechas) y el orden (fecha, precio, relevancia), y el nuevo ya no
- [ ] **Decidir qué pasa con la #56:** generar el seeder y correr los tests es una tarea técnica, no una historia. Según la regla de arriba, iría sin prefijo (`Generar el seeder y correr los tests`), y `US-EVT-05` quedaría libre para la próxima historia
- [x] **Unificar el título de la US-TKT-10:** en GitHub (#52) dice *Reintentar con otro medio si el pago se rechaza*, y en `user-stories.md` dice *Liberar los lugares si el pago se rechaza*
- [ ] **Confirmar las siglas propuestas** de auth, queue, notification y recommendation
