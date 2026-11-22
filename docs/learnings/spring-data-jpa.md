# Spring Data JPA

Lo que aprendimos al usar una clave primaria como protección contra duplicados (#152). Visto con Spring Boot 4.0.9.

- **Con un id asignado, `save` hace `merge`, no un INSERT.** Spring Data considera nueva una entidad solo si su id es `null`. Con un id que viene de afuera (como el `messageId` de un mensaje), una segunda entrega encuentra la fila, la actualiza sin error y la operación se repite. Para que el INSERT salga y choque contra la clave, la entidad implementa `Persistable` con un `isNew()` que vale `true` hasta `@PostPersist`/`@PostLoad`, y se guarda con `saveAndFlush`. Ejemplo: `notification-service/.../entity/ProcessedMessage.java`.
- **El duplicado se atrapa fuera del `@Transactional`.** La `DataIntegrityViolationException` deja la transacción marcada para rollback, así que atraparla adentro termina igual en `UnexpectedRollbackException`. Se atrapa en quien llama, que ya está fuera de la transacción. Ejemplo: `notification-service/.../messaging/PurchaseConfirmedListener.java`.
