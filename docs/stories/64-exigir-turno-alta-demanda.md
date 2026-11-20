# 64: US-TKT-21: Exigir el turno en eventos de alta demanda

**Tipo:** Story
**Estado:** borrador
**Funcionalidad:** Épica 7: Fila virtual y control de acceso con QR (#7)

Servicio: ticket-service · Involucra: queue-service, api-gateway · Consigna: 6 · Entrega 2

## Historia
Como **cliente**,
quiero **que en un evento de alta demanda solo pueda reservar quien tiene su turno de la fila virtual, y solo durante su ventana**,
para **que nadie se saltee la fila ni retenga su turno para siempre mientras otros esperan**.

## Contexto
La fila virtual (US-QUE-01, #147) ordena a los clientes y les da un turno con una ventana para comprar, pero hoy cualquiera puede llamar directo a reservar y la fila no serviría de nada.

## Criterios de aceptación
- **CA1: Con turno vigente**
  Dado un evento de alta demanda y un cliente cuyo turno para ese evento todavía no llegó a su `enabledUntil`
  Cuando reserva mandando el turno en el header `X-Queue-Token`
  Entonces la reserva funciona igual que en un evento común
- **CA2: Sin turno o con un turno que no sirve**
  Dado un evento de alta demanda
  Cuando el cliente reserva sin turno, con un turno vencido, adulterado o emitido para otro cliente u otro evento
  Entonces responde 403 `invalid_queue_token` y no se reserva ningún lugar
- **CA3: Evento común**
  Dado un evento que no es de alta demanda
  Cuando el cliente reserva, con turno o sin él
  Entonces la reserva no pide turno
- **CA4: La ventana termina con la reserva**
  Dado un cliente que reservó con su turno vigente
  Cuando paga después del `enabledUntil`
  Entonces el pago no pide turno, pero tiene el límite de la reserva (10 minutos desde que se reservó): pasado ese plazo responde 409 `reservation_expired`
- **CA5: Varias reservas en la misma ventana**
  Dado un cliente que abandonó su reserva y todavía no llegó a su `enabledUntil`
  Cuando vuelve a reservar
  Entonces se acepta, con el tope de entradas por cliente de siempre

## Fuera de alcance
- Emitir el turno y armar la fila: es la US-QUE-01 (#147).
- Pedir el turno para consultar la disponibilidad, pagar o cancelar.

## Notas técnicas
El turno es un JWT firmado por el queue-service, con cliente, evento y vencimiento (`enabledUntil`). El ticket-service lo valida sin llamar al queue-service (cómo se firma va en un ADR). El gateway reenvía el header. El evento se identifica con `event_sale.high_demand`.

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [ ] Review aprobada
- [ ] ADR de la firma del turno
- [ ] Documentación afectada actualizada (`endpoints.md`, `ticket-service.md`, request de Postman)
