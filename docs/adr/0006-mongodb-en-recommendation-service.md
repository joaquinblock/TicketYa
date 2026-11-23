# 0006: MongoDB en recommendation-service

- **Fecha:** 2026-11-23
- **Estado:** aceptada

## Contexto
La consigna pide que al menos un servicio use una base NoSQL, justificando la elección, y sugiere MongoDB para el catálogo de eventos o para las preferencias de los clientes. El resto del sistema usa MySQL (y Redis en queue-service) porque necesita transacciones, `UPDATE` atómicos y restricciones de unicidad (`quality-attributes/persistence.md`).

recommendation-service guarda dos cosas, siempre por cliente:

- **Preferencias** (US-REC-01): tipos de evento, participantes favoritos y ciudad. Se leen y se reemplazan enteras.
- **Historial de compras** (US-REC-02 y US-NOT-03): los eventos que compró y cuántas veces aparece cada participante, lugar y organización en lo que compró. Lo arman los avisos `purchase.confirmed` de RabbitMQ ([ADR 0004](0004-rabbitmq-para-avisos-entre-servicios.md)), que pueden llegar repetidos.

Ninguno de los dos necesita transacciones entre registros ni relaciones con otras tablas: los ids de usuario, evento y participante son de otros servicios (RFC-004).

## Decisión
recommendation-service usa MongoDB 8.0, con un documento por cliente en cada colección (`preferences` y `purchase_history`) y el `userId` como `_id`.

- Las listas (`eventTypes`, `participantIds`, `eventIds`) y los contadores (`counts.participants.<id>`, etc.) van dentro del documento.
- La idempotencia de los avisos se resuelve con operaciones atómicas sobre un solo documento: `upsert` con `$addToSet` para el historial, y `updateFirst` con filtro `countedEventIds: {$ne: eventId}` y `$inc` para los contadores.
- "Quienes compraron esto también compraron…" es una agregación (`$match` → `$unwind` → `$group`) sobre un índice multikey en `eventIds`.
- Los índices se crean al arrancar en `MongoIndexes`, sin `auto-index-creation`.

## Alternativas consideradas
- **MongoDB en event-service:** es el otro candidato que nombra la consigna, pero event-service ya estaba hecho en MySQL en la Entrega 1, con lugares, sectores y asientos muy relacionados. Migrarlo es más trabajo y más riesgo que empezar un servicio nuevo en MongoDB.
- **MySQL, como los demás:** las listas y los contadores por id serían tablas intermedias (`preferencia_tipo_evento`, `participante_favorito`, una fila por cliente y participante) y joins para algo que se lee siempre entero. Además no cumple con la base NoSQL que pide la consigna.
- **Redis:** sirve para datos efímeros como la fila virtual, pero las preferencias y el historial tienen que durar, y la co-compra necesita consultas por contenido de listas que Redis no resuelve sin armar índices a mano.

## Consecuencias
- Cada lectura es un solo documento por `_id`, sin joins. Agregar un campo a las preferencias no requiere migración.
- Las garantías dependen de que cada escritura toque un solo documento: MongoDB es atómico por documento. Si una funcionalidad necesita cambiar dos documentos juntos, hay que repensarla o usar transacciones de MongoDB, que piden un replica set.
- No hay esquema ni restricciones en la base: lo que valida los datos es el código (DTOs y servicios).
- Las claves de un mapa son texto, así que los contadores por id se guardan como `"42"` y se convierten al leerlos.
- El documento de historial crece con cada evento distinto que compra el cliente. Con ids numéricos, llegar al límite de 16 MB por documento haría falta cientos de miles de eventos, así que no es un problema real.
- Los tests de persistencia (`@DataMongoTest`) corren contra el MongoDB de `compose.yaml`, así que necesitan Docker prendido, igual que los de MySQL en los otros servicios.
