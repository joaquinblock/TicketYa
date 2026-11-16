# RFC-001: Modalidad de autenticación

- **Estado:** Abierto — a decidir antes de la Épica 9 (Seguridad JWT)
- **Relacionado:** #9, tabla `user` del `auth-service` (campo `idp_subject`)

## Contexto

El enunciado pide explícitamente un login externo (OAuth); como referencia se toma el de Google.

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

## Posible funcionamiento con Google

> Propuesta, no decidida. Las dudas de *Dudas abiertas* siguen sin resolver.

El ID token de Google dice **quién es** la persona (`sub`, email), pero no trae su rol ni sus permisos en TicketYa. Por eso el auth-service lo **canjea** por un JWT propio:

1. El front hace Google Sign-In y recibe un ID token de Google.
2. `POST /api/auth/google {idToken}`: el auth-service lo valida contra las claves públicas de Google (`iss=accounts.google.com`, `aud=<client id>`, `exp`, `email_verified`).
3. Busca el `User` por `idp_subject = sub`. Si no existe, aplica la regla del primer login (ver *Dudas abiertas*).
4. Emite el JWT de TicketYa (RS256, con la clave privada del auth-service): `sub`=userId, `role`, `permissions`, `organizationId`, `exp` de alrededor de 1 h. Responde `{accessToken, expiresIn}`.
5. El gateway valida **solo** el JWT de TicketYa, con la clave pública. Nunca recibe el de Google.

```mermaid
sequenceDiagram
    participant F as Front
    participant Go as Google
    participant A as auth-service
    participant G as api-gateway
    participant S as servicio
    F->>Go: Google Sign-In
    Go-->>F: ID token de Google
    F->>A: POST /api/auth/google {idToken}
    A->>A: valida el token y busca el user por sub
    A-->>F: JWT de TicketYa (rol y permisos)
    F->>G: Authorization: Bearer <JWT>
    G->>G: valida firma y vencimiento
    G->>S: X-User-Id, X-User-Role, X-User-Permissions
```

### Qué hay en cada servicio

| Servicio | Clase | Qué hace |
|---|---|---|
| auth-service | `GoogleIdTokenVerifier` | `NimbusJwtDecoder` contra el JWKS de Google, con validadores de issuer y audience |
| auth-service | `JwtTokenService` | Arma los claims a partir de `User` y `Role` y firma con el `JwtEncoder` de Spring |
| auth-service | `AuthController`, `AuthService` | `POST /api/auth/google`. `401 invalid_credentials` si el token de Google es inválido o está vencido |
| api-gateway | `SecurityConfig` | `oauth2ResourceServer().jwt()` con la clave pública. El filtro que valida ya lo trae Spring Security (`BearerTokenAuthenticationFilter`), así que no se escribe uno propio |
| api-gateway | Handlers de 401 y 403 | Responden `unauthorized` y `forbidden` con `ErrorDto` |
| api-gateway | `IdentityHeadersFilter` | Borra los `X-User-*` que mande el cliente y pone `X-User-Id`, `X-User-Role` y `X-User-Permissions` sacados del token |
| event-service y demás | `PermissionInterceptor` | Arma el `Caller` con esos headers en vez de llamar a `AuthClient`. Se puede mover a `common` para que lo use ticket-service |

- **Rutas públicas en el gateway:** `POST /api/auth/google`, la búsqueda y el detalle de eventos, `/docs/**`, swagger y health.
- **Configuración:** `GOOGLE_CLIENT_ID` y la clave privada van por variable de entorno, nunca en el repo.

### Riesgos

| Riesgo | Mitigación |
|---|---|
| Si los servicios exponen su propio puerto, se puede saltear el gateway y mandar headers falsos | Publicar solo el puerto del gateway en compose |
| Si cambian el rol de un usuario o lo dan de baja, sus permisos siguen en el token hasta que vence | `exp` corto |
| Los usuarios del seeder tienen `idp_subject = null` | Vincularlos por email verificado en el primer login |

### Cómo se probaría

- **auth-service:** ID token válido, vencido o con otra audience (con el decoder de Google mockeado), usuario nuevo y usuario dado de baja.
- **api-gateway** (como `InternalRoutesTest`):
  - sin token, `401`;
  - con un token adulterado o vencido, `401`;
  - con uno válido, el request llega con los headers;
  - el `X-User-Id` que mande el cliente se descarta.
- **Postman:** un request de login que guarde el token en una variable de la colección.

## Decisión

Pendiente. Cuando se decida, registrar la decisión y su porqué como ADR y actualizar el modelo de `user` si corresponde.

## Dudas abiertas

### ¿El primer login crea la cuenta directamente como cliente?

Si el `sub` de Google no está en `user`, la opción más segura es darlo de alta como `CLIENT`: nadie puede elegirse el rol.

- **Problema:** un organizador que entra antes de que lo carguen queda como cliente.
- **Endpoints separados por rol** (p. ej. uno para organizadores): no sirven, porque cualquiera con una cuenta de Google podría llamarlo y darse ese rol.
- **Alternativa:** un solo endpoint, y el rol sale de la base.
  - Si un admin precargó el email, se vincula por email verificado y conserva su rol.
  - Si ya había entrado como cliente, el admin lo asciende en la misma cuenta.
  - `ORGANIZER` ya incluye los permisos de comprador.
- **Costo:** el cambio de rol se aplica en el próximo token.

### ¿Cómo se prueba sin pasar por el login de Google?

Postman y los tests no pueden hacer Google Sign-In.

| Opción | Qué implica |
|---|---|
| **Endpoint solo en perfil `dev`** (`POST /api/auth/dev-login {userId}`) | Emite el JWT de TicketYa para un usuario del seeder. Riesgo: que quede activo en producción; se cubre con un test |
| **Solo Google real** | `id_token` del OAuth Playground pegado en Postman. Es lo más fiel, pero vence en 1 h y el equipo necesita un client id |
