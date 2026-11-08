# RFC: UUID público en los recursos de compra

## Resumen

Se propone agregar una columna `publicId` (UUID) a `Ticket`, `Order` y `ReturnRequest` y que sea el **único id que exponen los DTOs de respuesta**. La PK `Long` y las referencias internas no cambian (alternativa B).

**Estado:** propuesta. **Alcance:** ticket-service. **Confianza:** alta.

## Contexto

Hoy todos los recursos de compra usan PK `Long` autoincremental y todavía no hay ningún contrato público.

- `Ticket`, `Order` y `ReturnRequest` usan `@GeneratedValue(strategy = IDENTITY)`.
- `Ticket.qrCode` ya es un UUID aleatorio (`TicketSeeder.java:164`), porque un QR predecible permitiría falsificar entradas (`docs/architecture/ticket-service.md`).
- `Order` tiene una `idempotencyKey` UUID.
- `ticket-service/.../dto` y `controller` están vacíos.
- `Ticket.eventSectorId` y `seatId` apuntan a event-service por `Long`.

**Supuestos:** MySQL con Spring Boot 3 / Hibernate 6; el cliente consulta sus compras con `GET /orders/{id}` o `/tickets/{id}`; el usuario sale del JWT.

**Criterios:** seguridad real (no aparente), filtración de información de negocio, costo de implementarlo ahora, rendimiento de la BD, claridad para el equipo.

## Motivación

Los ids secuenciales permiten adivinar recursos y filtran el volumen de ventas. Un UUID resuelve las dos cosas.

- **No se pueden adivinar.** Quien tiene la entrada 1532 sabe que existen la 1531 y la 1533. Si un endpoint olvida verificar el dueño (IDOR), probar ids de a uno alcanza para ver compras ajenas.
- **No se filtra el volumen de ventas.** Si alguien compra una entrada al abrir la venta y otra al final, la resta de ids le dice cuántas se vendieron. Para una ticketera es información de negocio sensible.
- **Se genera sin la BD.** El id puede existir antes del `INSERT`, lo que ayuda en la saga entre servicios (pago, notificación) y en la idempotencia.
- **Hoy es casi gratis.** No hay DTOs ni controllers, así que no se rompe ningún contrato.

## Alternativas consideradas

Se elige la **B**. La **C** (autorizar siempre) es obligatoria en cualquier caso, pero sola no alcanza.

| Opción | Idea | Evaluación |
| --- | --- | --- |
| **A. UUID como PK** | `Ticket.id` pasa a ser `UUID` | Cambia todas las FK (`ticket` ↔ `orders`, `return_request`). Con v4 los inserts quedan desordenados en el índice clustered de InnoDB |
| **B. PK `Long` + `public_id UUID`** ✔ | Internamente se usa `Long`; la API muestra solo `publicId` | Es lo más común en la industria. Las FK y los joins no cambian |
| **C. Mantener `Long` + autorizar** | Verificar siempre que el recurso sea del usuario del JWT | Obligatoria igual, pero no evita que se filtre el volumen |
| **D. Ofuscar el `Long`** (Sqids/Hashids) | Codificar el id en un string | Es ofuscación, no seguridad. Agrega una dependencia y una clave que se puede filtrar |
| **E. Usar `qrCode` como id** | Ya es un UUID aleatorio | Descartada: el QR es la credencial de acceso, y ponerlo en URLs o logs equivale a regalar la entrada. Además es `NULL` hasta la emisión |

## Propuesta

Cada recurso de compra tiene dos ids: el `Long` interno para FK y joins, y el `publicId` para la API. Solo el segundo sale del servicio por HTTP.

**Entidades** (`Ticket`, `Order`, `ReturnRequest`):

```java
@Column(nullable = false, unique = true, updatable = false)
private UUID publicId;

@PrePersist
void assignPublicId() {
    if (publicId == null) {
        publicId = UUID.randomUUID();
    }
}
```

**Repositorios:** se agrega `findByPublicIdAndUserId(UUID publicId, Long userId)`, así un mismo método busca y autoriza.

**DTOs:** el campo se llama `id` pero el mapper lo llena **solo** con `publicId`. El `Long` nunca aparece en una respuesta.

**Endpoints:** `GET /orders/{id}` y `/tickets/{id}` reciben el UUID. Si el recurso no existe o es de otro usuario, la respuesta es `404`, para no confirmar que existe.

**Entre servicios:** las referencias internas (`eventSectorId`, `seatId`) y las FK siguen siendo `Long`.

## Riesgos y mitigaciones

El riesgo principal es creer que el UUID reemplaza la autorización. El cambio es reversible y barato ahora, pero caro cuando haya un frontend consumiendo la API.

| Riesgo | Probabilidad / impacto | Mitigación |
| --- | --- | --- |
| Creer que un UUID es seguridad: se filtra por screenshots, links o logs y el IDOR sigue ahí | Alta / alto | Chequeo de ownership obligatorio y un test por endpoint: otro usuario → `404` |
| Hacerlo a medias: UUID en `Ticket` pero `/orders/{id}` con `Long`, y el volumen se sigue filtrando | Media / alto | Aplicarlo a todos los recursos de compra expuestos |
| Confundir los dos ids: `findById(publicId)` o devolver el `Long` por error | Media / medio | Solo `findByPublicId...` en los controllers; el mapper es el único que arma el DTO |
| Rendimiento: 16 bytes y fragmentación del índice | Baja / bajo | Con B el UUID está en un índice secundario; la PK clustered sigue siendo `Long` |

## Preguntas abiertas

- ¿Otros servicios (payment, notification, queue) guardan el id del ticket o de la orden? Si es así, hay que definir qué id viaja en la saga. El veredicto no cambia.
- ¿El `userId` de cada endpoint sale siempre del JWT y nunca de la URL?
- ¿`ReturnRequest` se expone por id o solo a través de su orden?
- Lo único que cambiaría el veredicto: que la consigna del TP exija ids numéricos en la API (poco probable).

## Plan de implementación

- [ ] Agregar `publicId` + `@PrePersist` a `Ticket`, `Order` y `ReturnRequest`, y actualizar `TicketSeeder`
- [ ] Agregar `findByPublicIdAndUserId` en los repositorios
- [ ] Crear los DTOs con `id` = `publicId` y los mappers correspondientes
- [ ] Endpoints por UUID y test de ownership (otro usuario → `404`) en cada uno
- [ ] Documentarlo en `docs/architecture/ticket-service.md`, junto a la sección de `qr_code`
