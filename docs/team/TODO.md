# TODO list

## Entrega 1
- [ ] Revisar error handling y comentarios en [PR #31](https://github.com/joaquinblock/TicketYa/pull/31)
- [ ] Generar lista histórica de issues
- [x] Subir diagramas de flujo: secuencia de compra en [`purchase-sequence.md`](../architecture/purchase-sequence.md) y módulos en [`microservices.md`](../architecture/microservices.md)
- [ ] Subir DER (el de ticket-service está en [`ticket-service.md`](../architecture/ticket-service.md); faltan los otros servicios)
- [ ] Subir diagrama de clases
- [ ] Chequear si todos los endpoints se encuentran en Swagger
- [x] Chequear si todos los test corren
- [x] Instrucciones para levantar los servicios (README, *Cómo ejecutar*)
- [ ] Historias de usuario de event-service y auth-service: [`user-stories.md`](../user-stories.md) solo tiene las de ticket-service. Las tiene que escribir el grupo, no se inventan
- [ ] US-EVT-05 (#56) es una tarea técnica, no una historia: decidir el nombre (ver [`user-story-naming.md`](user-story-naming.md))

## Entrega 2
- [ ] Test E2E automatizado de la compra completa (evento → publicar → reservar → pagar → QR → devolución) levantando los servicios reales con Testcontainers o el `compose.yaml`. Para la Entrega 1 alcanza con los tests de integración de cada servicio contra MySQL más la prueba manual con Postman
