# 148: US-AUTH-04: Asignar personal de acceso a un evento

**Tipo:** Story
**Estado:** borrador
**Funcionalidad:** Épica 7: Fila virtual y control de acceso con QR (#7)

Servicio: auth-service · Involucra: event-service, ticket-service · Sirve a: US-TKT-19 (#62) · Consigna: 8 · Entrega 2

## Historia
Como **organizador**,
quiero **asignar a mi personal de acceso a uno de mis eventos**,
para **que solo la gente que elijo pueda validar entradas en la puerta de ese evento**.

## Contexto
El rol `ACCESS_STAFF` existe, pero el personal no pertenece a ninguna organización ni está asignado a eventos, así que cualquiera con ese rol podría validar entradas de cualquier evento.

## Criterios de aceptación
- **CA1: Asignar**
  Dado un personal de acceso de mi organización y un evento de mi organización
  Cuando lo asigno al evento
  Entonces responde 201. Asignarlo otra vez responde 200 y no lo duplica
- **CA2: Personal ajeno o inexistente**
  Dado un personal de acceso de otra organización: 403 `forbidden`. Un usuario que no es personal de acceso: 404 `access_staff_not_found`
- **CA3: Evento ajeno o inexistente**
  Dado un evento de otra organización: 403 `forbidden`. Un evento que no existe: 404 `event_not_found`
- **CA4: Validar solo donde está asignado**
  Dado un personal asignado al evento A
  Cuando valida entradas
  Entonces puede en A, y en cualquier otro evento recibe 403 `staff_not_assigned`
- **CA5: El event-service no responde**
  Dado que el event-service está caído o no responde a tiempo
  Cuando el organizador asigna personal
  Entonces responde 503 `service_unavailable` y no se crea la asignación

## Fuera de alcance
- Dar de alta personal de acceso: sale del seeder.
- Quitar una asignación o listar las asignaciones de un evento.

## Notas técnicas
Asignar pide `EVENT_MANAGE`. Necesita `AccessStaff` y `AccessAssignment` de la #19. El ticket-service consulta la asignación por un endpoint interno del auth-service (US-TKT-19).

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [ ] Review aprobada
- [ ] Documentación afectada actualizada (`auth-service.md`, `endpoints.md`, request de Postman)
