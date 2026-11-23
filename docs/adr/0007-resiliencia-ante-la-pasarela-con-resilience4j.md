# 0007: Resiliencia ante la pasarela con Resilience4j

- **Fecha:** 2026-11-23
- **Estado:** aceptada

## Contexto
La consigna pide reintentos, timeouts y fallback ante fallos de servicios externos, empezando por la pasarela de pagos, y justificar patrones como Circuit Breaker. Para el negocio es el punto más sensible: si el cliente decidió comprar, un problema momentáneo de la pasarela no puede frustrar el cobro ni hacerle perder la reserva (US-PAY-11).

Restricciones:

- **Sin doble cobro.** Un timeout no dice si la pasarela cobró. Por eso el pago queda `PENDING` y todo reintento usa la misma `idempotency_key`, que la pasarela no cobra dos veces ([`payment-service.md`](../architecture/payment-service.md), *`PENDING` como estado de duda*).
- **Tiempo acotado.** ticket-service espera a payment-service hasta 20 s (`read-timeout`), y el intento tiene que resolverse dentro del margen de 1 minuto de la reserva. El peor caso con reintentos tiene que quedar por debajo de esos 20 s.
- **Llamada síncrona.** `PaymentGateway.charge` es bloqueante, igual que lo sería un cliente HTTP a una pasarela real.

## Decisión
`ResilientPaymentGateway` envuelve a la pasarela con Resilience4j (2.3.0, la versión del BOM de Spring Cloud), armado a mano sin el starter ni anotaciones. Cubre cobros y reintegros. Para los reintegros, los reintentos también usan la clave derivada del `ticketId`, de modo que la pasarela no acredita dos veces ante timeout o duplicación.

Los decoradores van así: `Retry( CircuitBreaker( TimeLimiter( charge ) ) )`.

| Pieza | Configuración (`payment.gateway.resilience`) | Por qué |
|---|---|---|
| TimeLimiter | `timeout: 3s`, cancela la llamada | Corta una pasarela colgada. La llamada corre en un hilo virtual y se interrumpe |
| Retry | `max-attempts: 3`, `wait-between-attempts: 500ms`, solo ante `PaymentGatewayUnavailableException` | Un rechazo es una respuesta, no una falla: nunca se reintenta. Un error nuestro tampoco |
| CircuitBreaker | Ventana de 10 llamadas, mínimo 5, abre con 50% de fallas, 10 s abierto, 2 llamadas de prueba | Ante fallas recurrentes deja de esperar y responde al instante |

- **Peor caso:** 3 × 3 s + 2 × 0,5 s ≈ 10 s, debajo de los 20 s de ticket-service.
- **El circuit breaker va adentro del retry,** así cuenta cada intento. Por afuera contaría un pedido entero, y harían falta 5 clientes esperando 10 s cada uno para que se abra.
- **Con el circuito abierto** el retry no reintenta (`CallNotPermittedException` no está en la lista) y se responde 503 sin llamar a la pasarela.
- **Fallback:** 503 `payment_gateway_unavailable`, con el pago `PENDING`. No se inventa un resultado: el cliente conserva la reserva y puede reintentar el mismo intento sin riesgo de doble cobro. Se eligió 503 y no 500 porque es un problema temporal de un servicio externo que se puede reintentar, no un error nuestro.
- **Recuperación:** pasados los 10 s, el circuito deja pasar 2 llamadas de prueba y, si salen bien, se cierra solo, sin reiniciar el servicio.

## Alternativas consideradas
- **Resilience4j con anotaciones** (`@Retry`, `@CircuitBreaker`, `@TimeLimiter`): menos código, pero depende de proxies de AOP con un orden de aspectos implícito, y `@TimeLimiter` exige devolver `CompletableFuture`. Armado a mano, el orden queda escrito en el código y se testea sin levantar Spring.
- **Spring Cloud CircuitBreaker:** agrega una capa de abstracción sobre Resilience4j que no necesitamos, porque hay una sola implementación.
- **Spring Retry y un circuit breaker propio:** menos dependencias, pero el circuit breaker quedaría a nuestro cargo, con sus estados y su concurrencia.
- **Solo reintentos, sin circuit breaker:** con la pasarela caída, cada cliente esperaría los 10 s completos y se acumularían hilos ocupados.

## Consecuencias
- `PaymentService` no cambia para cobros: ya convertía la excepción de la pasarela en 503 y dejaba el pago `PENDING`. `RefundService` convierte el agotamiento de reintentos en `502 refund_failed` y conserva el reintegro `FAILED`. La resiliencia queda en el borde, detrás de la interfaz `PaymentGateway`, y una pasarela real recibe lo mismo sin tocar las reglas.
- Una pasarela real tiene que traducir sus fallas de red a `PaymentGatewayUnavailableException`. Si tira otra excepción, no se reintenta ni cuenta para el circuit breaker.
- Una llamada cortada por timeout puede haber llegado a la pasarela. Es seguro por la `idempotency_key`, pero el resultado de esa llamada se descarta.
- El estado del circuit breaker es por instancia. Con varias réplicas, cada una abre su circuito por su cuenta.
- Al cambiar `timeout` o `max-attempts` hay que recalcular el peor caso contra el `read-timeout` de ticket-service. Si se baja ese timeout, pasa lo mismo. Los dos lados se definen juntos en la US-TKT-23.
- Para probarlo sin una pasarela real, el mock tiene demora y caída configurables y, con el perfil `dev`, `PUT /internal/mock/gateway` las cambia en ejecución.
