# TODO list

## Entrega 1
- [ ] Revisar error handling y comentarios en [PR #31](https://github.com/joaquinblock/TicketYa/pull/31)
- [ ] Generar lista histórica de issues
- [x] Subir diagramas de flujo: secuencia de compra en [`purchase-sequence.md`](../architecture/purchase-sequence.md) y módulos en [`modules.md`](../architecture/modules.md)
- [x] Subir DER: uno por servicio en `auth-service.md`, `event-service.md`, `ticket-service.md` y `payment-service.md`, con las referencias entre bases y los datos solapados
- [ ] Subir diagrama de clases
- [x] Chequear si todos los endpoints se encuentran en Swagger: [`endpoints.md`](../architecture/endpoints.md) se generó de las anotaciones OpenAPI
- [x] Chequear si todos los test corren
- [x] Instrucciones para levantar los servicios (README, *Cómo ejecutar*)
- [x] Historias de usuario de todo el sistema en [`user-stories.md`](../user-stories.md): las 58 de las dos entregas, con criterios de aceptación
- [ ] US-EVT-05 (#56) es una tarea técnica, no una historia: decidir el nombre (ver [`user-story-naming.md`](user-story-naming.md))

## Entrega 2
- [ ] Test E2E automatizado de la compra completa (evento → publicar → reservar → pagar → QR → devolución) levantando los servicios reales con Testcontainers o el `compose.yaml`. Para la Entrega 1 alcanza con los tests de integración de cada servicio contra MySQL más la prueba manual con Postman
