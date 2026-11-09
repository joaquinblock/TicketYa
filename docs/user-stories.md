# Features y user stories

> Por ahora contiene las historias de ticket-service y payment-service. Los demás servicios se agregan con el mismo formato y su propio prefijo (`US-EVT`, etc.).

Cada historia tiene un servicio responsable y, si hace falta, los servicios que también participan. Indica además la consigna del enunciado (NF = consideraciones no funcionales), la épica de GitHub y la entrega, según la tabla de *Modalidad de entrega* del enunciado.

El actor de una historia es siempre una persona que obtiene algo de valor (cliente, organizador, administrador o personal de acceso). Lo que solo es integración entre servicios se marca como `Tipo: técnica` e indica a qué historia de negocio le sirve.

Las historias `US-PAY` cubren solo lo que hace payment-service y se prueban contra su propia API, sin necesitar a ticket-service. `Sirve a` indica la historia de ticket-service que las usa, y ahí está el flujo completo que ve el cliente.

Los criterios de aceptación describen lo que se ve desde afuera: respuestas, códigos de error y estados que devuelve la API. Lo que el cliente nunca ve (tablas internas, contadores, bloqueos, jobs) va en **Notas técnicas**, solo cuando suma.

Las decisiones de diseño y las dependencias entre servicios están en la documentación de cada servicio (por ejemplo, [`ticket-service.md`](architecture/ticket-service.md)).

## Índice

| ID | Historia | Feature | Servicio | Involucra | Entrega | Épica |
|---|---|---|---|---|---|---|
| [US-TKT-01](#us-tkt-01-crear-el-inventario-al-publicar-un-evento) | Crear el inventario al publicar un evento | F1. Inventario | ticket-service | event-service | 1 | #3 |
| [US-TKT-02](#us-tkt-02-consultar-la-disponibilidad) | Consultar la disponibilidad | F1. Inventario | ticket-service | — | 1 | #4 |
| [US-TKT-03](#us-tkt-03-reservar-asientos-numerados) | Reservar asientos numerados | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-04](#us-tkt-04-reservar-lugares-en-un-sector-no-numerado) | Reservar lugares en un sector no numerado | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-05](#us-tkt-05-reintentar-la-reserva-sin-duplicarla) | Reintentar la reserva sin duplicarla | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-06](#us-tkt-06-limitar-las-entradas-por-cliente) | Limitar las entradas por cliente | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-07](#us-tkt-07-vender-solo-cuando-el-evento-está-a-la-venta) | Vender solo cuando el evento está a la venta | F2. Reserva temporal | ticket-service | event-service | 1 | #4 |
| [US-TKT-08](#us-tkt-08-liberar-las-reservas-vencidas) | Liberar las reservas vencidas | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-28](#us-tkt-28-abandonar-una-reserva) | Abandonar una reserva | F2. Reserva temporal | ticket-service | — | 1 | #4 |
| [US-TKT-09](#us-tkt-09-pagar-una-orden-y-recibir-las-entradas) | Pagar una orden y recibir las entradas | F3. Pago y emisión | ticket-service | payment-service | 1 | #4 |
| [US-TKT-10](#us-tkt-10-reintentar-con-otro-medio-si-el-pago-se-rechaza) | Reintentar con otro medio si el pago se rechaza | F3. Pago y emisión | ticket-service | payment-service | 1 | #4 |
| [US-TKT-11](#us-tkt-11-reintentar-el-pago-sin-que-se-cobre-dos-veces) | Reintentar el pago sin que se cobre dos veces | F3. Pago y emisión | ticket-service | payment-service | 1 | #4 |
| [US-TKT-12](#us-tkt-12-ver-mis-compras-y-mis-entradas) | Ver mis compras y mis entradas | F3. Pago y emisión | ticket-service | — | 1 | #4 |
| [US-TKT-13](#us-tkt-13-mantener-actualizado-el-estado-agotado) | Mantener actualizado el estado agotado | F3. Pago y emisión | ticket-service | event-service | 1 | #4 |
| [US-TKT-14](#us-tkt-14-solicitar-la-devolución-de-una-entrada) | Solicitar la devolución de una entrada | F4. Devoluciones | ticket-service | — | 1 | #5 |
| [US-TKT-15](#us-tkt-15-listar-las-solicitudes-de-devolución) | Listar las solicitudes de devolución | F4. Devoluciones | ticket-service | — | 1 | #5 |
| [US-TKT-16](#us-tkt-16-aprobar-una-devolución) | Aprobar una devolución | F4. Devoluciones | ticket-service | payment-service | 1 | #5 |
| [US-TKT-17](#us-tkt-17-rechazar-una-devolución) | Rechazar una devolución | F4. Devoluciones | ticket-service | — | 1 | #5 |
| [US-TKT-18](#us-tkt-18-reintegrar-las-entradas-al-cancelar-un-evento) | Reintegrar las entradas al cancelar un evento | F5. Cancelación de eventos | ticket-service | event-service, payment-service | 1 | #5 |
| [US-TKT-19](#us-tkt-19-validar-el-qr-en-el-ingreso) | Validar el QR en el ingreso | F6. Ingreso al evento | ticket-service | auth-service | 2 | #7 |
| [US-TKT-20](#us-tkt-20-consultar-el-reporte-de-un-evento) | Consultar el reporte de un evento | F7. Reportes | ticket-service | event-service, auth-service | 2 | #8 |
| [US-TKT-21](#us-tkt-21-exigir-el-turno-en-eventos-de-alta-demanda) | Exigir el turno en eventos de alta demanda | F8. Fila virtual | ticket-service | queue-service | 2 | #7 |
| [US-TKT-22](#us-tkt-22-compensar-fallos-después-de-cobrar) | Compensar fallos después de cobrar | F9. Consistencia y resiliencia | ticket-service | payment-service | 2 | #10 |
| [US-TKT-23](#us-tkt-23-responder-claro-si-el-payment-service-no-responde) | Responder claro si el payment-service no responde | F9. Consistencia y resiliencia | ticket-service | payment-service | 2 | #10 |
| [US-TKT-24](#us-tkt-24-publicar-compras-y-cancelaciones) | Publicar compras y cancelaciones | F10. Integración con otros servicios | ticket-service | notification-service, recommendation-service | 2 | #8 |
| [US-TKT-25](#us-tkt-25-exponer-el-historial-de-compras) | Exponer el historial de compras (técnica) | F10. Integración con otros servicios | ticket-service | recommendation-service | 2 | #8 |
| [US-TKT-26](#us-tkt-26-restringir-las-operaciones-por-rol) | Restringir las operaciones por rol | F11. Seguridad y trazabilidad | ticket-service | api-gateway, auth-service | 2 | #9 |
| [US-TKT-27](#us-tkt-27-registrar-requests-y-operaciones-críticas) | Registrar requests y operaciones críticas | F11. Seguridad y trazabilidad | ticket-service | event-service, payment-service | 2 | #10 |
| [US-PAY-01](#us-pay-01-cobrar-una-orden-con-el-medio-de-pago-elegido) | Cobrar una orden con el medio de pago elegido | F12. Cobro | payment-service | — | 1 | #4 |
| [US-PAY-02](#us-pay-02-saber-por-qué-se-rechazó-un-pago) | Saber por qué se rechazó un pago | F12. Cobro | payment-service | — | 1 | #4 |
| [US-PAY-03](#us-pay-03-no-pagar-dos-veces-si-se-reintenta) | No pagar dos veces si se reintenta | F12. Cobro | payment-service | — | 1 | #4 |
| [US-PAY-04](#us-pay-04-consultar-los-pagos-de-una-orden) | Consultar los pagos de una orden | F12. Cobro | payment-service | — | 1 | #4 |
| [US-PAY-05](#us-pay-05-simular-la-pasarela-con-resultados-predecibles) | Simular la pasarela con resultados predecibles (técnica) | F12. Cobro | payment-service | — | 1 | #4 |
| [US-PAY-06](#us-pay-06-recibir-el-dinero-de-una-devolución-aprobada) | Recibir el dinero de una devolución aprobada | F13. Reintegros | payment-service | — | 1 | #5 |
| [US-PAY-07](#us-pay-07-recibir-el-dinero-de-un-evento-cancelado) | Recibir el dinero de un evento cancelado | F13. Reintegros | payment-service | — | 1 | #5 |
| [US-PAY-08](#us-pay-08-recibir-el-dinero-si-se-cobró-y-la-compra-no-se-concretó) | Recibir el dinero si se cobró y la compra no se concretó | F13. Reintegros | payment-service | — | 1 | #4 |
| [US-PAY-09](#us-pay-09-no-recibir-un-reintegro-duplicado-ni-mayor-a-lo-cobrado) | No recibir un reintegro duplicado ni mayor a lo cobrado | F13. Reintegros | payment-service | — | 1 | #5 |
| [US-PAY-10](#us-pay-10-cobrar-y-reintegrar-solo-desde-ticket-service) | Cobrar y reintegrar solo desde ticket-service (técnica) | F14. Acceso interno | payment-service | api-gateway | 1 | #1 |
| [US-PAY-11](#us-pay-11-tolerar-problemas-momentáneos-de-la-pasarela) | Tolerar problemas momentáneos de la pasarela | F15. Resiliencia | payment-service | — | 2 | #10 |
| [US-PAY-12](#us-pay-12-resolver-los-pagos-sin-respuesta-y-anular-los-de-una-reserva-vencida) | Resolver los pagos sin respuesta y anular los de una reserva vencida | F15. Resiliencia | payment-service | — | 2 | #10 |
| [US-PAY-13](#us-pay-13-reintentar-los-reintegros-fallidos) | Reintentar los reintegros fallidos | F15. Resiliencia | payment-service | — | 2 | #10 |
| [US-PAY-14](#us-pay-14-autenticar-las-llamadas-internas-y-restringir-la-consulta-a-administradores) | Autenticar las llamadas internas y restringir la consulta a administradores (técnica) | F16. Seguridad y trazabilidad de pagos | payment-service | ticket-service, api-gateway, auth-service | 2 | #9 |
| [US-PAY-15](#us-pay-15-registrar-cobros-y-reintegros) | Registrar cobros y reintegros | F16. Seguridad y trazabilidad de pagos | payment-service | — | 2 | #10 |

## F1. Inventario

### US-TKT-01: Crear el inventario al publicar un evento

Como organizador quiero que al publicar mi evento sus lugares queden disponibles para empezar a vender entradas.

Servicio: ticket-service · Involucra: event-service · Consigna: 2, 3 · Épica: #3 · Entrega 1

**Criterios de aceptación**
- Al publicar el evento, cada asiento de los sectores numerados habilitados queda disponible para la venta.
- Al publicar el evento, cada sector no numerado habilitado queda con su capacidad completa disponible.
- Una vez publicado el evento, se puede reservar aunque el event-service no esté disponible.
- Si la publicación se informa dos veces, los lugares no se duplican y la operación responde con éxito.
- Si el evento vuelve a `PUBLISHED` después de estar `SUSPENDED` o `SOLD_OUT`, se conservan las ventas y reservas que ya tenía.

**Notas técnicas:** `event_seat` en `AVAILABLE` por asiento; `event_sector_stock` con `capacity` y `taken` en 0. Idempotente por la clave de cada fila: se crean solo las que faltan. Los datos de venta se copian a `event_sale` y `event_sector_price` (ver *Copia de los datos de venta* en `ticket-service.md`).

### US-TKT-02: Consultar la disponibilidad

Como cliente quiero ver qué lugares quedan libres para elegir dónde comprar.

Servicio: ticket-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- En un sector numerado se informa qué asientos están disponibles y cuáles no.
- En un sector no numerado se informa la cantidad de lugares libres.
- Un asiento con una reserva vencida se informa como disponible.
- Los lugares de un sector no numerado con reserva vencida vuelven a figurar libres a más tardar 1 minuto después del vencimiento.
- Si el evento no tiene lugares a la venta: 404 `resource_not_found`.

**Notas técnicas:** se lee siempre de la base, sin cache. Los lugares libres son `capacity - taken`, y `taken` baja cuando corre el job de la US-TKT-08.

## F2. Reserva temporal

### US-TKT-03: Reservar asientos numerados

Como cliente quiero reservar asientos específicos para pagarlos sin que otro me los gane.

Servicio: ticket-service · Consigna: 4, 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Los asientos elegidos quedan reservados para el cliente durante 10 minutos.
- La orden queda en `PENDING`, con su vencimiento y con un total igual a la suma de los precios.
- Cada asiento genera una entrada en `RESERVED`, sin QR, con el precio vigente al reservar. Un cambio de precio posterior no la afecta.
- Un asiento con una reserva vencida se puede reservar.
- La reserva es todo o nada: si un asiento no está disponible, responde 409 `seat_unavailable` y no se reserva ninguno.
- Si un asiento no existe en el evento: 404 `resource_not_found`.
- Con K asientos libres y N compras simultáneas por ellos (N > K), de clientes e intentos de compra distintos y con 2 instancias del servicio levantadas, hay exactamente K exitosas y N−K reciben 409 `seat_unavailable`.

**Notas técnicas:** `event_seat` con `@Version` queda en `RESERVED`. Un conflicto de bloqueo optimista se traduce a `seat_unavailable`, nunca a `data_conflict`.

### US-TKT-04: Reservar lugares en un sector no numerado

Como cliente quiero reservar lugares en una zona sin asientos para asegurar mi entrada mientras pago.

Servicio: ticket-service · Consigna: 4, 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Los lugares pedidos quedan reservados para el cliente durante 10 minutos.
- La orden queda en `PENDING` y cada lugar genera una entrada en `RESERVED`, sin asiento y sin QR.
- La reserva es todo o nada: si no alcanza el cupo, responde 409 `sector_sold_out` y no se reserva ningún lugar.
- La cantidad pedida debe ser mayor a 0; si no: 400 `validation_error`.
- Con K lugares libres y N compras simultáneas de un lugar cada una (N > K), de clientes e intentos de compra distintos y con 2 instancias del servicio levantadas, hay exactamente K exitosas y N−K reciben 409 `sector_sold_out`. El caso del último lugar es K=1.
- Nunca se reservan más lugares que la capacidad del sector.

**Notas técnicas:** `UPDATE` atómico sobre `event_sector_stock`, con un `CHECK` que protege `taken`.

### US-TKT-05: Reintentar la reserva sin duplicarla

Como cliente quiero poder reintentar la reserva si se corta la conexión para no terminar con dos reservas.

Servicio: ticket-service · Consigna: 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Si el cliente repite un mismo intento de compra, recibe la orden ya creada y no se reservan más lugares.
- Si dos pedidos del mismo intento llegan a la vez, se crea una sola orden y ambos la reciben.
- Si la clave del intento ya pertenece a otro cliente: 409 `data_conflict`, sin exponer la orden.
- Si la misma clave se reusa con otros lugares: 409 `data_conflict` y no se reserva nada.

**Notas técnicas:** `orders.idempotency_key` UNIQUE, generada por el cliente en cada intento.

### US-TKT-06: Limitar las entradas por cliente

Como organizador quiero limitar cuántas entradas compra cada cliente para que la venta sea justa.

Servicio: ticket-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Si el evento define un límite, las entradas `RESERVED`, `ISSUED` y `USED` del cliente, más las que pide, no pueden superarlo.
- Las entradas `VOIDED` y `REFUNDED` no cuentan.
- Si se supera el límite: 409 `ticket_limit_exceeded` y no se reserva nada.
- Dos compras exactamente simultáneas del mismo cliente pueden superar el límite (ver *Limitaciones conocidas* en `ticket-service.md`). Nunca se vende dos veces un mismo lugar.

**Notas técnicas:** el límite es `event_sale.max_tickets_per_customer`.

### US-TKT-07: Vender solo cuando el evento está a la venta

Como organizador quiero que solo se reserven lugares cuando la venta está abierta para respetar lo que publiqué.

Servicio: ticket-service · Involucra: event-service · Consigna: 3, 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Solo se aceptan reservas si el evento está `PUBLISHED`.
- En cualquier otro estado, incluido `SUSPENDED`: 409 `event_not_on_sale`.
- Antes de la apertura de la venta: 409 `sale_not_started`.
- Cuando el event-service informa un cambio de estado del evento, la venta se bloquea o se habilita desde ese momento.

**Notas técnicas:** el estado y la apertura se leen de `event_sale`, y el event-service avisa cada cambio.

### US-TKT-08: Liberar las reservas vencidas

Como cliente quiero que se liberen los lugares de las reservas que no se pagaron a tiempo para poder comprarlos.

Servicio: ticket-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Una orden `PENDING` que supera su vencimiento pasa a `EXPIRED`, sus entradas a `VOIDED` y sus lugares vuelven a estar disponibles, a más tardar 1 minuto después del vencimiento.
- Una orden que ya pasó a `PAID` no vence.
- Con 2 instancias del servicio, cada orden vencida libera sus lugares una sola vez.
- Un lugar que ya tomó otra orden no se libera.
- La reserva nunca se extiende, aunque tenga un pago sin resultado.

**Notas técnicas:** job cada 30 segundos que limpia `event_seat` y descuenta `taken`. La transición sigue la decisión *Una sola transición desde `PENDING`* de `ticket-service.md`.

### US-TKT-28: Abandonar una reserva

Como cliente quiero abandonar mi reserva para liberar los lugares si cambio de idea, sin esperar a que venza.

Servicio: ticket-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Al abandonar una orden propia en `PENDING`, la orden pasa a `CANCELLED`, sus entradas a `VOIDED` y sus lugares vuelven a estar disponibles en el momento.
- Esas entradas dejan de contar para el límite por cliente.
- Si hay un pago en curso: 409 `payment_in_progress` y la reserva sigue vigente.
- Si el pedido se repite sobre una orden ya `CANCELLED`, responde con éxito y no libera lugares otra vez.
- Si la orden no existe o es de otro cliente: 404 `order_not_found`.
- Si la orden está `EXPIRED`: 409 `reservation_expired`. Si está `PAID`, `REJECTED` o `FAILED`: 409 `order_not_cancellable`.

**Notas técnicas:** `POST /api/orders/{id}/cancel`. Ver *Abandonar una reserva* en `ticket-service.md`.

## F3. Pago y emisión

### US-TKT-09: Pagar una orden y recibir las entradas

Como cliente quiero pagar mi reserva para recibir mis entradas con un QR único.

Servicio: ticket-service · Involucra: payment-service · Consigna: 4, 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- El cliente paga una orden propia indicando el medio de pago, por el total de la orden.
- Si el pago se aprueba, la orden pasa a `PAID` y sus entradas a `ISSUED`, cada una con un QR distinto e impredecible.
- Si la reserva venció antes de intentar el cobro: 409 `reservation_expired` y no se cobra nada.
- Los intentos nuevos se aceptan hasta 1 minuto antes del vencimiento. Después: 409 `payment_window_closed` y no se cobra nada.
- Si la reserva venció antes de que se confirme el pago, no se emiten entradas, la orden queda `EXPIRED`, el pago se reintegra y el cliente recibe 409 `reservation_expired`.
- Nunca quedan dos entradas `ISSUED` para el mismo asiento de un evento, aunque otro cliente haya tomado el asiento tras el vencimiento.
- Si la orden no existe o es de otro cliente: 404 `order_not_found`.
- Si la orden está `EXPIRED`: 409 `reservation_expired`. Si está `REJECTED`, `CANCELLED` o `FAILED`: 409 `order_not_payable`.

**Notas técnicas:** se cobra por `order_id` con `orders.total`; `orders.payment_method` guarda el medio del pago aprobado; `qr_code` UUID; `event_seat` pasa a `SOLD`. El reintegro es por `ticket` con motivo `PURCHASE_COMPENSATION`. El mecanismo está en las decisiones *Una sola transición desde `PENDING`* y *Verificar el asiento antes de emitir* de `ticket-service.md`.

### US-TKT-10: Reintentar con otro medio si el pago se rechaza

Como cliente quiero poder pagar con otro medio si me rechazan uno para no perder los lugares que reservé.

Servicio: ticket-service · Involucra: payment-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Si la pasarela rechaza el pago, el cliente recibe 422 `payment_rejected` con el motivo del rechazo.
- Con menos de 3 rechazos, la orden sigue `PENDING`, sus lugares siguen reservados sin extender el vencimiento y el cliente puede intentar con otro medio, en la misma orden.
- Al tercer rechazo, la orden pasa a `REJECTED`, sus entradas a `VOIDED` y sus lugares vuelven a estar disponibles.
- Repetir un intento ya rechazado devuelve el mismo rechazo y no cuenta como otro.
- Un pago sin respuesta de la pasarela no es un rechazo (ver US-TKT-23).

**Notas técnicas:** cada intento usa una clave distinta. Ver *Intentos de pago* en `ticket-service.md`.

### US-TKT-11: Reintentar el pago sin que se cobre dos veces

Como cliente quiero poder reintentar el pago si se corta la conexión para no pagar dos veces.

Servicio: ticket-service · Involucra: payment-service · Consigna: 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Si el cliente repite un mismo intento de pago, no se le cobra otra vez.
- Si la orden ya está `PAID`, recibe las mismas entradas con sus mismos QR.
- Si dos pedidos de pago de la misma orden llegan a la vez, se cobra una sola vez y se emite un solo juego de entradas.
- Mientras un intento no tenga resultado, solo se puede reintentar ese mismo. Un intento distinto recibe 409 `payment_in_progress` y no se cobra.

**Notas técnicas:** el cliente genera una clave por intento y la repite en sus reintentos. `orders.payment_attempt_key` guarda la del intento sin resultado.

### US-TKT-12: Ver mis compras y mis entradas

Como cliente quiero ver mis compras y mis entradas para tener el QR a mano al ingresar.

Servicio: ticket-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Cada compra muestra evento, estado de la orden, total, medio de pago y detalle de entradas (sector, asiento, precio y estado).
- El QR solo se muestra en entradas `ISSUED`.
- Un cliente solo ve sus propias compras; si pide una ajena: 404 `order_not_found`.

**Notas técnicas:** el medio de pago sale de `orders.payment_method`, así el listado no depende del payment-service.

### US-TKT-13: Mantener actualizado el estado agotado

Como organizador quiero que mi evento figure agotado cuando corresponda para que el público sepa si quedan entradas.

Servicio: ticket-service · Involucra: event-service · Consigna: 1, 7 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Cuando no queda ningún lugar disponible ni reservado, el evento pasa a `SOLD_OUT`.
- Una reserva en curso no agota el evento.
- Si una devolución aprobada libera un lugar de un evento `SOLD_OUT`, el evento vuelve a `PUBLISHED`.
- Si el aviso al event-service falla, la compra o la devolución no se deshace y el aviso se reintenta hasta que se aplique.

**Notas técnicas:** el job de la US-TKT-08 reintenta los avisos pendientes.

## F4. Devoluciones

### US-TKT-14: Solicitar la devolución de una entrada

Como cliente quiero pedir la devolución de una entrada para recuperar mi dinero si no puedo asistir.

Servicio: ticket-service · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- La solicitud queda en `PENDING` y la entrada sigue `ISSUED` hasta que se resuelva.
- Se aceptan solicitudes de entradas `ISSUED`, hasta 72 horas antes del evento, con el evento `PUBLISHED`, `SOLD_OUT` o `SUSPENDED`.
- Fuera de esas condiciones: 409 `refund_not_allowed`.
- Si la entrada no existe o es de otro cliente: 404 `ticket_not_found`.
- Si ya hay una solicitud `PENDING` para esa entrada: 409 `return_request_already_pending`.
- Después de un rechazo se puede volver a solicitar, si sigue en plazo.

**Notas técnicas:** la fecha y el estado salen de `event_sale`.

### US-TKT-15: Listar las solicitudes de devolución

Como administrador quiero ver las solicitudes pendientes para resolverlas en orden.

Servicio: ticket-service · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- El listado es paginado, se puede filtrar por estado y muestra primero las más antiguas.
- Por defecto muestra las solicitudes `PENDING`.
- Cada solicitud muestra entrada, evento, solicitante y fecha.

### US-TKT-16: Aprobar una devolución

Como administrador quiero aprobar una devolución para liberar el lugar y que se reintegre el dinero.

Servicio: ticket-service · Involucra: payment-service · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- Se pide el reintegro del precio pagado por esa entrada.
- Cuando el payment-service acepta el reintegro, la solicitud pasa a `APPROVED`, con quién la resolvió y cuándo, y la entrada a `REFUNDED`.
- El lugar vuelve a estar disponible.
- Las demás entradas de la misma orden no cambian.
- Si la solicitud no existe: 404 `return_request_not_found`. Si ya no está `PENDING`: 409 `refund_not_allowed`.
- Si la entrada ya está `USED` al aprobar: 409 `ticket_already_used` y no se pide ningún reintegro.
- Si el payment-service no responde: 503 `service_unavailable`, la solicitud sigue `PENDING` y el administrador puede reintentar sin que se genere un segundo reintegro.

**Notas técnicas:** ver *Aprobar una devolución: primero payment* en `ticket-service.md`. Reintegro por `ticket_id`, con motivo `RETURN_REQUEST` y `return_request_id`; `refund.ticket_id` UNIQUE impide duplicados.

### US-TKT-17: Rechazar una devolución

Como administrador quiero rechazar una devolución que no corresponde para que la entrada siga vigente.

Servicio: ticket-service · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- La solicitud pasa a `REJECTED`, con quién la resolvió y cuándo.
- La entrada sigue `ISSUED`, el lugar no se libera y no se pide ningún reintegro.
- Si la solicitud no existe: 404 `return_request_not_found`. Si ya no está `PENDING`: 409 `refund_not_allowed`.

## F5. Cancelación de eventos

### US-TKT-18: Reintegrar las entradas al cancelar un evento

Como cliente quiero que me devuelvan el dinero si se cancela un evento para no perder lo que pagué.

Servicio: ticket-service · Involucra: event-service, payment-service · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- Las entradas `ISSUED` y `USED` del evento pasan a `REFUNDED` y se pide el reintegro del precio pagado por cada una.
- Las órdenes `PENDING` pasan a `CANCELLED`, sus entradas a `VOIDED` y sus lugares se liberan.
- Si una orden se cancela mientras se confirma su pago, no se emiten entradas y el pago se reintegra.
- Las solicitudes de devolución `PENDING` pasan a `CANCELLED`, así el cliente no las ve como rechazadas.
- Las entradas ya `REFUNDED` no generan otro reintegro.
- Si la cancelación se informa dos veces, no se genera ningún reintegro duplicado.
- Si falla el reintegro de una entrada, se procesan las demás y la cancelación se puede repetir para completar las faltantes.

**Notas técnicas:** event-service avisa la cancelación. Motivo de reintegro `EVENT_CANCELLED`.

## F6. Ingreso al evento

### US-TKT-19: Validar el QR en el ingreso

Como personal de acceso quiero escanear el QR de una entrada para dejar pasar solo a quien corresponde.

Servicio: ticket-service · Involucra: auth-service · Consigna: 8 · Épica: #7 · Entrega 2

**Criterios de aceptación**
- Si la entrada es válida pasa a `USED`, y queda registrado quién la validó y cuándo.
- Si el QR no existe, es de otro evento o la entrada está `VOIDED` o `REFUNDED`: 400 `invalid_qr`.
- Si la entrada ya está `USED`: 409 `ticket_already_used`.
- Si el personal no está asignado al evento: 403 `staff_not_assigned`, tal como lo responde el auth-service.
- Con dos escaneos simultáneos del mismo QR, exactamente uno se acepta y el otro recibe 409 `ticket_already_used`.

**Notas técnicas:** `@Version` en `ticket`. Un conflicto de bloqueo optimista se traduce a `ticket_already_used`.

## F7. Reportes

### US-TKT-20: Consultar el reporte de un evento

Como organizador quiero ver cómo se vendió mi evento para conocer su resultado.

Servicio: ticket-service · Involucra: event-service, auth-service · Consigna: 12 · Épica: #8 · Entrega 2

**Criterios de aceptación**
- Por cada sector se informan entradas vendidas (`ISSUED` y `USED`) y devueltas (`REFUNDED`), porcentaje de ocupación y recaudación, además del total del evento.
- La ocupación cuenta solo entradas vendidas, no reservas.
- La recaudación es la suma de los precios pagados, sin las entradas `REFUNDED`.
- Un organizador solo puede ver eventos de su organización; si no: 403 `forbidden`.

**Notas técnicas:** no se usa `taken` para la ocupación, porque incluye reservas. La capacidad de cada sector viene del event-service.

## F8. Fila virtual

### US-TKT-21: Exigir el turno en eventos de alta demanda

Como cliente quiero que en un evento de alta demanda se respete el orden de la fila para comprar de forma justa.

Servicio: ticket-service · Involucra: queue-service · Consigna: 6 · Épica: #7 · Entrega 2

**Criterios de aceptación**
- En un evento de alta demanda, reservar requiere un turno vigente de ese cliente para ese evento.
- Sin turno, con turno vencido o de otro cliente o evento: 403 `invalid_queue_token`.
- Los eventos que no son de alta demanda no lo requieren.

**Notas técnicas:** token con vencimiento emitido por el queue-service, validado sin llamarlo en cada compra. El evento se identifica con `event_sale.high_demand`.

## F9. Consistencia y resiliencia

### US-TKT-22: Compensar fallos después de cobrar

Como cliente quiero que me devuelvan el dinero si la compra falla después de cobrarme para no pagar por entradas que no recibí.

Servicio: ticket-service · Involucra: payment-service · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Si el pago se aprobó y la emisión falla por cualquier motivo, no queda ninguna entrada emitida y se reintegra el dinero.
- La orden termina en `FAILED` y sus lugares se liberan, salvo que ya estuviera `EXPIRED` o `CANCELLED`. El cliente no la ve como pago rechazado.
- Los pedidos de reintegro se reintentan hasta tener respuesta, sin generar duplicados.
- Si el reintegro no se completa, queda registrado en el log centralizado para resolverlo a mano.

**Notas técnicas:** motivo de reintegro `PURCHASE_COMPENSATION`. Amplía el caso simple de la US-TKT-09.

### US-TKT-23: Responder claro si el payment-service no responde

Como cliente quiero un mensaje claro si no se pudo confirmar mi pago para saber que no perdí la reserva.

Servicio: ticket-service · Involucra: payment-service · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Si no se puede confirmar el pago: 503 `service_unavailable`, la orden sigue `PENDING` y sus lugares siguen reservados hasta el vencimiento.
- El cliente puede reintentar ese mismo intento sin riesgo de un doble cobro.
- La orden vence igual aunque el pago siga sin resultado. Si después resulta aprobado, no se emiten entradas y se reintegra el dinero.

**Notas técnicas:** timeout y reintentos limitados con la misma `idempotency_key`, con un tiempo total menor al margen de 1 minuto. Ver *Manda la reserva* en `ticket-service.md`.

## F10. Integración con otros servicios

### US-TKT-24: Publicar compras y cancelaciones

Como cliente quiero enterarme cuando se confirma mi compra o se cancela un evento para saber qué pasó con mis entradas.

Servicio: ticket-service · Involucra: notification-service, recommendation-service · Consigna: 10, 11 · Épica: #8 · Entrega 2

**Criterios de aceptación**
- Al confirmarse una compra y al reintegrarse las entradas de un evento cancelado, se avisa para que se notifique al cliente.
- Si el aviso no se puede enviar, la compra o la cancelación no falla y queda registrado en el log centralizado.
- Un aviso repetido se puede detectar para no notificar dos veces.

**Notas técnicas:** mensaje en RabbitMQ después de confirmar la transacción, con identificadores para descartar duplicados. También lo consume el recommendation-service.

### US-TKT-25: Exponer el historial de compras

El recommendation-service necesita consultar el historial de compras para sugerir eventos. El valor para el cliente está en la historia de recomendaciones de ese servicio, que todavía no está escrita.

Tipo: técnica · Servicio: ticket-service · Involucra: recommendation-service · Consigna: 10 · Épica: #8 · Entrega 2

**Criterios de aceptación**
- Se informan cliente y evento de las órdenes `PAID`.
- Las órdenes en otros estados no se incluyen.
- La consulta solo está disponible entre servicios.

**Notas técnicas:** endpoint interno, no se expone a través del gateway.

## F11. Seguridad y trazabilidad

### US-TKT-26: Restringir las operaciones por rol

Como administrador quiero que cada operación esté limitada a su rol para que nadie actúe sobre lo que no le corresponde.

Servicio: ticket-service · Involucra: api-gateway, auth-service · Consigna: NF · Épica: #9 · Entrega 2

**Criterios de aceptación**
- Cliente: US-TKT-02 a 05, 09 a 12, 14 y 28. Administrador: US-TKT-15 a 17. Personal de acceso: US-TKT-19. Organizador: US-TKT-20.
- Las operaciones entre servicios (US-TKT-01, 18 y 25) no están disponibles para usuarios finales.
- Sin token válido: 401 `unauthorized`. Con un rol que no corresponde: 403 `forbidden`.
- Un cliente no puede operar en nombre de otro.

**Notas técnicas:** el usuario sale del JWT y reemplaza al header `X-User-Id` de la Entrega 1.

### US-TKT-27: Registrar requests y operaciones críticas

Como administrador quiero poder seguir una operación entre servicios para investigar qué pasó con una compra.

Servicio: ticket-service · Involucra: event-service, payment-service · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Cada request HTTP se registra con endpoint, usuario, código de respuesta y tiempo de procesamiento.
- Compra, devolución, cancelación e ingreso al evento se registran en el log centralizado con un identificador de correlación.
- Ese identificador permite seguir la misma operación en todos los servicios que participan.
- En el log no se registran QR ni datos del medio de pago.

## F12. Cobro

### US-PAY-01: Cobrar una orden con el medio de pago elegido

Como cliente quiero pagar mi orden con el medio que elijo para completar la compra.

Servicio: payment-service · Sirve a: US-TKT-09 · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- `POST /internal/payments` con `orderId`, `amount`, `paymentMethod` e `idempotencyKey` cobra ese monto con ese medio.
- Si la pasarela aprueba: 201 con el pago en `APPROVED`, su medio, su monto y el identificador de la pasarela.
- Los medios aceptados son `CREDIT_CARD`, `DEBIT_CARD` y `ACCOUNT_MONEY`.
- Si falta un campo, el medio no existe o el monto no es mayor a 0: 400 `validation_error` y no se cobra nada.
- Si la orden ya tiene otro pago `PENDING` o `APPROVED`: 409 `data_conflict` y no se cobra nada.
- Una orden admite otro pago si los anteriores fueron rechazados.
- Ni el pedido ni la respuesta incluyen datos de la tarjeta.

**Notas técnicas:** la fila se inserta en `PENDING` antes de llamar a la pasarela. Ver *Varios pagos por orden, un solo aprobado* en `payment-service.md`.

### US-PAY-02: Saber por qué se rechazó un pago

Como cliente quiero saber por qué me rechazaron un pago para decidir con qué medio volver a intentar.

Servicio: payment-service · Sirve a: US-TKT-10 · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Si la pasarela rechaza: 422 `payment_rejected`, con `reason` y `rejectedAttempts` en `detail`.
- `reason` es el motivo informado por la pasarela.
- `rejectedAttempts` es la cantidad de pagos rechazados de esa orden, incluido el actual.
- El pago queda en `REJECTED` con su motivo.

**Notas técnicas:** el motivo se guarda en `failure_reason`. El tope de 3 rechazos lo aplica ticket-service con `rejectedAttempts`.

### US-PAY-03: No pagar dos veces si se reintenta

Como cliente quiero que un reintento por un corte de conexión no me cobre otra vez para pagar una sola vez.

Servicio: payment-service · Sirve a: US-TKT-11 · Consigna: 5 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Con la misma clave y los mismos datos, responde con el mismo status y cuerpo que la primera vez, sin crear otro pago ni volver a cobrar.
- Si el pago original fue rechazado, repite el 422 `payment_rejected` y no suma otro rechazo.
- Si la misma clave llega con otra orden, otro monto u otro medio: 409 `data_conflict` y no se cobra nada.
- Si dos pedidos con la misma clave llegan a la vez, se cobra una sola vez y existe un solo pago.
- Si el pago de esa clave sigue sin resultado: 503 `payment_gateway_unavailable`, sin cobrar de nuevo, y se puede reintentar.

**Notas técnicas:** `payment.idempotency_key` UNIQUE. La clave también se reenvía a la pasarela. Ver *Idempotencia del cobro* en `payment-service.md`.

### US-PAY-04: Consultar los pagos de una orden

Como administrador quiero ver los pagos de una orden para resolver reclamos.

Servicio: payment-service · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- `GET /api/payments?orderId=` devuelve todos los pagos de la orden, incluidos los rechazados, con medio, monto, estado, motivo de rechazo y fecha.
- Si la orden no tiene pagos: 404 `payment_not_found`.
- Si falta `orderId`: 400 `invalid_parameter`.
- `GET /internal/payments?orderId=` da la misma respuesta para las consultas entre servicios.

**Notas técnicas:** en la Entrega 1 no se valida el rol. La restricción a administradores está en la US-PAY-14.

### US-PAY-05: Simular la pasarela con resultados predecibles

El equipo necesita una pasarela simulada para probar cobros, rechazos y reintegros sin depender de una real. El valor para el cliente está en las US-PAY-01, 02 y 06 a 08.

Tipo: técnica · Servicio: payment-service · Consigna: NF · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Los cobros con el medio configurado en `payment.mock.rejected-method` se rechazan con un motivo.
- Los cobros con cualquier otro medio y los reintegros se aprueban, cada uno con un identificador de pasarela único.
- Repetir un cobro con la misma clave no genera otro cobro en la pasarela.
- Para la Entrega 2, `payment.mock.delay-ms` demora las respuestas y otra propiedad simula la caída de la pasarela. Son necesarias para probar la US-PAY-11.

**Notas técnicas:** el mock vive detrás de un adaptador, para reemplazarlo por una pasarela real sin tocar el resto.

## F13. Reintegros

### US-PAY-06: Recibir el dinero de una devolución aprobada

Como cliente quiero recibir el dinero de la entrada que devolví para no perder lo que pagué.

Servicio: payment-service · Sirve a: US-TKT-16 · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- `POST /internal/refunds` con `ticketId`, `orderId`, `amount`, `reason` `RETURN_REQUEST` y `returnRequestId` reintegra ese monto sobre el pago aprobado de la orden: 201 con el reintegro en `CREDITED`.
- El reintegro es por entrada: las demás entradas de la orden no cambian y se pueden reintegrar por separado.
- Si `reason` es `RETURN_REQUEST` y falta `returnRequestId`: 400 `validation_error`.
- Si la orden no tiene pagos: 404 `payment_not_found`.
- Si la orden no tiene un pago aprobado: 409 `payment_not_refundable`.
- Si la pasarela no acredita el reintegro: 502 `refund_failed` y el reintegro queda `FAILED` con su motivo.

**Notas técnicas:** `refund.reason` y un `CHECK` para `return_request_id`. Ver *Motivo explícito* en `payment-service.md`.

### US-PAY-07: Recibir el dinero de un evento cancelado

Como cliente quiero recibir el dinero de mis entradas si se cancela un evento para no perder lo que pagué.

Servicio: payment-service · Sirve a: US-TKT-18 · Consigna: 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- Con `reason` `EVENT_CANCELLED`, cada entrada se reintegra por separado: 201 con el reintegro en `CREDITED`.
- Si se envía `returnRequestId` con ese motivo: 400 `validation_error`.
- Si falla el reintegro de una entrada, no afecta a las demás.
- Una entrada con un reintegro `FAILED` por devolución se puede reintegrar por la cancelación, y los datos se actualizan con los del pedido nuevo.

### US-PAY-08: Recibir el dinero si se cobró y la compra no se concretó

Como cliente quiero recibir el dinero si me cobraron y la compra no se concretó para no pagar por entradas que no recibí.

Servicio: payment-service · Sirve a: US-TKT-09, US-TKT-22 · Consigna: 4 · Épica: #4 · Entrega 1

**Criterios de aceptación**
- Con `reason` `PURCHASE_COMPENSATION`, cada entrada de la orden se reintegra sobre su pago aprobado: 201 con el reintegro en `CREDITED`.
- Si se envía `returnRequestId` con ese motivo: 400 `validation_error`.
- La respuesta informa el motivo, distinto al de una devolución o una cancelación.

**Notas técnicas:** payment-service nunca llama a ticket-service: el reintegro siempre lo pide ticket-service, una vez por entrada.

### US-PAY-09: No recibir un reintegro duplicado ni mayor a lo cobrado

Como cliente quiero que cada entrada se reintegre una sola vez y por lo que pagué para que el dinero devuelto sea el correcto.

Servicio: payment-service · Sirve a: US-TKT-16, US-TKT-18, US-TKT-22 · Consigna: 5, 7 · Épica: #5 · Entrega 1

**Criterios de aceptación**
- Si la entrada ya tiene un reintegro que no falló y llegan los mismos datos: 200 con el reintegro existente, sin acreditar de nuevo.
- Si la entrada ya tiene un reintegro que no falló y llegan otros datos: 409 `data_conflict`, sin acreditar de nuevo.
- Si la entrada tiene un reintegro `FAILED`, el pedido lo reintenta sobre el mismo reintegro: 201 con el reintegro en `CREDITED` o 502 `refund_failed`.
- Si llegan dos pedidos a la vez por la misma entrada, se acredita una sola vez.
- La suma de los reintegros de un pago nunca supera lo cobrado, aun con pedidos simultáneos de entradas distintas. Si la superaría: 409 `payment_not_refundable`. Los reintegros `FAILED` no cuentan.

**Notas técnicas:** `refund.ticket_id` UNIQUE. Ver *Un solo reintegro por entrada* en `payment-service.md`.

## F14. Acceso interno

### US-PAY-10: Cobrar y reintegrar solo desde ticket-service

Si un usuario pudiera llamar directamente a estas rutas, podría generar cobros o devoluciones sin una orden real. El valor para el cliente está en las US-PAY-01 y 06 a 08.

Tipo: técnica · Servicio: payment-service · Involucra: api-gateway · Consigna: NF · Épica: #1 · Entrega 1

**Criterios de aceptación**
- `/internal/payments` y `/internal/refunds` a través del gateway responden 404.
- `GET /api/payments` sí está disponible a través del gateway.
- Un test automatizado verifica ambos comportamientos.

**Notas técnicas:** el gateway no reenvía `/internal/**`. La protección de la Entrega 2 está en la US-PAY-14.

## F15. Resiliencia

### US-PAY-11: Tolerar problemas momentáneos de la pasarela

Como cliente quiero que un problema momentáneo de la pasarela no frustre mi compra para no perder la reserva por una falla ajena.

Servicio: payment-service · Sirve a: US-TKT-23 · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Si la pasarela responde dentro de los reintentos, el resultado es el de un cobro normal y se cobra una sola vez.
- Si no hay respuesta al agotar los reintentos: 503 `payment_gateway_unavailable` y el pago sigue `PENDING`.
- Un pago sin respuesta no cuenta en `rejectedAttempts`.
- Con la pasarela caída, los pedidos responden 503 `payment_gateway_unavailable` sin esperar todo el tiempo máximo.
- Cuando la pasarela se recupera, los cobros vuelven a procesarse sin reiniciar el servicio.

**Notas técnicas:** timeout, reintentos con la misma clave y circuit breaker. El tiempo total, sumando reintentos, tiene que ser menor al margen de 1 minuto. Ver *`PENDING` como estado de duda* en `payment-service.md`.

### US-PAY-12: Resolver los pagos sin respuesta y anular los de una reserva vencida

Como cliente quiero que un pago sin respuesta se resuelva para que no me quede un cobro sin entradas.

Servicio: payment-service · Sirve a: US-TKT-23 · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Repetir una clave con el pago `PENDING` consulta el resultado en la pasarela, sin cobrar de nuevo. Si hay resultado, responde como un cobro normal. Si no: 503 `payment_gateway_unavailable`.
- `POST /internal/payments/cancel` con `idempotencyKey` responde 200 con el estado final del pago.
- El pago pasa a `CANCELLED` solo si la pasarela confirma la cancelación.
- Si la pasarela no contesta, responde `PENDING` y se puede volver a pedir.
- Si el pago ya tenía resultado, responde ese estado (`APPROVED`, `REJECTED` o `CANCELLED`) sin modificarlo.
- Si la clave no existe: 404 `payment_not_found`.

**Notas técnicas:** ver *Anular un intento sin resultado* en `payment-service.md`. Si el pago resulta `APPROVED`, el reintegro lo pide ticket-service.

### US-PAY-13: Reintentar los reintegros fallidos

Como cliente quiero que un reintegro que falló se vuelva a intentar para recibir mi dinero.

Servicio: payment-service · Sirve a: US-TKT-22 · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Si la pasarela falla momentáneamente, el reintegro se reintenta y termina `CREDITED`, acreditado una sola vez.
- Al agotar los reintentos: 502 `refund_failed` y el reintegro queda `FAILED` con su motivo.
- Volver a pedir un reintegro `FAILED` lo reintenta sobre el mismo reintegro, sin acreditar dos veces.

**Notas técnicas:** los reintentos usan la misma clave ante la pasarela, para que un timeout no acredite dos veces.

## F16. Seguridad y trazabilidad de pagos

### US-PAY-14: Autenticar las llamadas internas y restringir la consulta a administradores

La red interna sola no alcanza para proteger el cobro y los reintegros. El valor para el cliente está en las US-PAY-01 y 06 a 08.

Tipo: técnica · Servicio: payment-service · Involucra: ticket-service, api-gateway, auth-service · Consigna: NF · Épica: #9 · Entrega 2

**Criterios de aceptación**
- Sin una credencial de servicio válida, `/internal/**` responde 401 `unauthorized`.
- `GET /api/payments` sin token válido: 401 `unauthorized`. Con un rol que no sea administrador: 403 `forbidden`.
- En el `docker-compose` general, el puerto de payment-service no se publica y solo se accede desde la red interna.

**Notas técnicas:** se propone un secreto compartido en un header, por variable de entorno. El mecanismo se confirma en la épica #9.

### US-PAY-15: Registrar cobros y reintegros

Como administrador quiero poder seguir un cobro o un reintegro entre servicios para investigar reclamos.

Servicio: payment-service · Sirve a: US-TKT-27 · Consigna: NF · Épica: #10 · Entrega 2

**Criterios de aceptación**
- Cada cobro y cada reintegro se registra en el log centralizado con su resultado.
- El registro usa el identificador de correlación recibido, así se sigue la misma operación desde ticket-service.
- En el log no se registran datos del medio de pago, salvo su tipo.
