# 62: US-TKT-19: Validar el QR en el ingreso

**Tipo:** Story
**Estado:** aprobado
**Funcionalidad:** Épica 7: Fila virtual y control de acceso con QR (#7)

Servicio: ticket-service · Involucra: auth-service · Consigna: 8 · Entrega 2

## Historia
Como **personal de acceso**,
quiero **escanear el QR de una entrada en la puerta de un evento**,
para **dejar pasar solo a quien tiene una entrada válida para ese evento, una sola vez**.

## Contexto
Las entradas pagadas quedan `ISSUED` con un QR único (US-TKT-09), pero nadie las controla en la puerta. Sin esto, una captura del QR alcanza para que entren dos personas.

## Criterios de aceptación
- **CA1: Entrada válida**
  Dado una entrada `ISSUED` del evento y un personal de acceso asignado a ese evento
  Cuando escanea su QR para ese evento
  Entonces responde 200 con sector, fila y asiento (`null` en un sector no numerado), y la entrada queda `USED` con quién la validó y cuándo
- **CA2: QR que no sirve para este evento**
  Dado un QR que no existe, que es de otro evento o de una entrada `VOIDED` o `REFUNDED`
  Cuando se escanea
  Entonces responde 400 `invalid_qr`, sin decir cuál de esos casos es
- **CA3: Entrada ya usada**
  Dado una entrada `USED`
  Cuando se vuelve a escanear
  Entonces responde 409 `ticket_already_used` y no cambia quién la validó ni cuándo
- **CA4: Personal no asignado o sin permiso**
  Dado un personal de acceso que no está asignado al evento
  Cuando escanea un QR de ese evento
  Entonces responde 403 `staff_not_assigned`, tal como lo responde el auth-service, y la entrada sigue `ISSUED`.
  Con un rol sin `TICKET_VALIDATE`: 403 `forbidden`
- **CA5: El auth-service no responde**
  Dado que el auth-service está caído o no responde a tiempo
  Cuando se escanea un QR
  Entonces responde 503 `service_unavailable` y la entrada sigue `ISSUED`
- **CA6: Dos escaneos a la vez**
  Dado una entrada `ISSUED`
  Cuando llegan dos escaneos simultáneos de su QR, aunque sea a instancias distintas
  Entonces exactamente uno responde 200 y el otro 409 `ticket_already_used`

## Fuera de alcance
- Validar sin conexión, deshacer un ingreso o volver a entrar con la misma entrada.
- Controlar la fecha y hora del evento al escanear.
- Asignar el personal al evento: es la US-AUTH-04 (#148).

## Notas técnicas
`POST /api/tickets/check-in` con body `{ "eventId": 1, "qrCode": "..." }`. Dos escaneos a la vez se resuelven con un `update` condicional (`where status = ISSUED`): el que recibe 0 filas responde `ticket_already_used`.

CA1, CA2, CA3 y CA6 ya están en `main` (#150). Faltan CA4 y CA5, que llegan con la US-AUTH-04 (#148).

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [ ] Review aprobada
- [ ] Documentación afectada actualizada (`endpoints.md`, `ticket-service.md`, request de Postman)
