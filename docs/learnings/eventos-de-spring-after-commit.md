# Eventos de Spring después del commit

Lo que aprendimos al publicar `purchase.confirmed` desde el pago de ticket-service (#67). Visto en Spring 7.0.10, la versión de Boot 4.0.9.

- **`@TransactionalEventListener` también funciona con `TransactionTemplate`.** No hace falta `@Transactional`: con `TransactionOperations` la sincronización de transacciones está activa igual. Si la transacción se marca `setRollbackOnly()`, el listener `AFTER_COMMIT` no corre. Ejemplo: `OrderPaymentService.issueTicketsIfThePlacesAreStillHeld`.
- **Una excepción en `AFTER_COMMIT` no le llega al que llamó.** `AFTER_COMMIT` corre dentro de `afterCompletion`, y `TransactionSynchronizationUtils.invokeAfterCompletion` atrapa el `Throwable` y lo loguea como `TransactionSynchronization.afterCompletion threw exception`. Para que el log diga qué falló (por ejemplo, el id de la orden), hay que atraparla en el listener.
- **`AFTER_COMMIT` corre antes de devolver la conexión al pool.** Algo lento en el listener, como un broker caído que tarda el `connection-timeout`, deja la conexión a la base ocupada todo ese tiempo, aunque la transacción ya se haya confirmado.
- **Disparar el evento solo donde cambia el estado.** Si se publica donde el `UPDATE` condicional devuelve 1 (`markPendingOrderAsPaid(...) == 1`), los pagos repetidos o concurrentes no publican sin necesidad de más controles.
