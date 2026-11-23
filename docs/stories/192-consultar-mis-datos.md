# 192: US-AUTH-06: Consultar mis datos con el token

**Tipo:** Story
**Estado:** aprobado
**Funcionalidad:** Épica 9 (#9)

Servicio: auth-service · Involucra: api-gateway · Sirve a: joaquinblock/TicketYa-UI#8 · Consigna: NF · Entrega 2

## Historia
Como **cliente**,
quiero **ver mi nombre en la app después de iniciar sesión**,
para **saber con qué cuenta estoy comprando**.

## Contexto
El front muestra el nombre del usuario en el header. El JWT trae solo `sub`, `role` y `permissions`, y `GET /internal/users/{id}` no pasa por el gateway. Sin este endpoint, el front no tiene de dónde sacar el nombre, sobre todo con `dev-login`, que no pasa por Google.

## Criterios de aceptación
- **CA1: Mis datos**
  Dado un token válido de cualquier rol
  Cuando pido `GET /api/users/me`
  Entonces responde 200 con `{ userId, email, firstName, lastName }` del usuario del token
- **CA2: Sin token**
  Dado un request sin token, o con uno vencido o adulterado
  Entonces responde 401 `unauthorized`
- **CA3: Usuario dado de baja**
  Dado que el usuario del token está dado de baja
  Entonces responde 404 `user_not_found`, como `/internal/users/{id}`

## Fuera de alcance
- El rol y los permisos: el front ya los tiene en el JWT.
- Editar los datos.

## Notas técnicas
Reusa `UserContactResponse` y `UserService.findContact`. No lleva `@RequiresPermission`: alcanza con un usuario autenticado (`Caller`).

## Definition of Done
- [ ] Cada criterio tiene un test que pasa
- [ ] Request de Postman con assertions
- [ ] `auth-service.md`, `endpoints.md` y `user-stories.md` actualizados
