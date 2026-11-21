# Lente: concurrencia y persistencia

Se corre porque el triage vio un riesgo concreto en el diff. Concentrate en ese riesgo y en lo que el cambio agrega. Lo que ya existía y no se tocó no es de esta review. Leé también `_formato.md`, que está en esta misma carpeta.

## Qué leer

- `docs/quality-attributes/concurrency-and-idempotency.md` y `docs/quality-attributes/persistence.md`: son las reglas que el equipo eligió y por qué.
- `docs/architecture/<servicio>.md` y, si toca compras, `docs/architecture/purchase-sequence.md`.
- El diff y las entidades, repositorios y servicios que toca.

## Qué buscar

1. **Dos requests a la vez.** Para cada estado que el cambio modifica, imaginá dos hilos que llegan juntos: el mismo pago dos veces, dos usuarios por el último asiento, un pago y un vencimiento de reserva. ¿Qué garantiza que uno solo gane? Tiene que ser la base (`UPDATE ... WHERE estado = ?` que devuelve 1, `@Version`, `UNIQUE`), no un `if` en memoria seguido de un `save`.
2. **Límites de la transacción.** Qué queda adentro y qué afuera. Un request HTTP o un mensaje al broker dentro de una transacción abierta retiene la conexión (ver `communication.md`). Un efecto que se ejecuta antes del commit puede quedar hecho aunque la transacción se revierta.
3. **Rollback.** Si algo falla a mitad de camino, ¿qué queda escrito? ¿Hay una compensación (reintegro, liberar el asiento) y está probada?
4. **Idempotencia.** Repetir la misma operación con la misma clave da el mismo resultado y no duplica efectos (entradas, mensajes, cobros).
5. **Esquema.** Las garantías importantes están en la base (`UNIQUE`, `CHECK`, `NOT NULL`) y no solo en el código. Si hay entidades nuevas, revisá los índices de las consultas nuevas y las referencias por id entre servicios, sin FK física.
6. **Consultas.** N+1 en listados, cargas `LAZY` fuera de la transacción, `findAll` sin límite.

Si el comportamiento depende de cómo propaga Spring las transacciones o de cómo hace flush Hibernate, confirmalo en la versión del jar del proyecto antes de reportarlo.
