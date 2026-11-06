# RFC-001: Modalidad de autenticación

- **Estado:** Abierto — a decidir antes de la Épica 9 (Seguridad JWT)
- **Relacionado:** #9, tabla `user` del `auth-service` (campo `idp_subject`)

## Contexto

El diagrama de contexto del enunciado incluye un **"Proveedor de identidad (autenticación externa)"**, y el modelo de datos tiene en `user` el campo `idp_subject`: el identificador del usuario en ese proveedor (el claim `sub` del JWT que emite). Por eso `user` no tiene columna de contraseña.

Flujo previsto con IdP externo:

1. El usuario se loguea en el IdP (Keycloak, Auth0, Google, etc.), no en TicketYa.
2. El IdP le entrega un JWT con el claim `sub` (ID único y estable de la persona en el IdP).
3. El backend lee el `sub`, busca el `User` con ese `idp_subject` y obtiene su rol y sus datos.

No se usa el email como vínculo porque puede cambiar o no venir en el token; el `sub` es estable y único.

Para la Entrega 1 no hace falta resolverlo: el enunciado pide "sin autenticación (el usuario se identifica por parámetro o encabezado)". Mientras tanto `idp_subject` queda en `null`.

## Opciones

| Opción | Qué implica |
|---|---|
| **IdP externo** (lo que dice el diagrama) | Se usa `idp_subject`; los servicios validan el JWT como *resource server*. Más realista, pero hay que levantar algo como Keycloak en Docker |
| **Login propio en el auth-service** | El auth-service emite el JWT. Se saca `idp_subject` y se agrega `password_hash`. Más simple de armar |

## Decisión

Pendiente. Cuando se decida, registrar la decisión y su porqué como ADR y actualizar el modelo de `user` si corresponde.
