# ADR-002: `dev-login` para obtener tokens sin Google

- **Estado:** Aceptado
- **Fecha:** 17/11/2026
- **Relacionado:** [RFC-001](../rfc/RFC-001-auth-modality.md), ADR-001 (perfil `dev` en Docker), US-AUTH-03, épica #9

## Contexto

El login de TicketYa es con Google (RFC-001): el front obtiene un ID token de Google y el auth-service lo canjea por un JWT propio. Pero casi todo lo que hay que probar no puede depender de Google:

| Qué hay que probar | Por qué Google no sirve |
|---|---|
| Cada rol (admin, organizador, personal de acceso, cliente) | Los usuarios del `AuthSeeder` tienen emails ficticios: nadie entra a ellos con Google |
| Tests de integración y colecciones de Postman | Un ID token de Google se obtiene desde un navegador y vence en 1 h: no se puede generar en un script |
| Prueba de carga | Hacen falta cientos de usuarios con token |
| Trabajo en paralelo | El gateway, los roles y los PRs de otros servicios no pueden esperar a que el login con Google esté terminado |

## Decisión

El auth-service expone `POST /api/auth/dev-login`, que emite el JWT de TicketYa de cualquier usuario activo **sin verificar credenciales**:

```http
POST /api/auth/dev-login
{ "userId": 2 }

200 { "accessToken": "eyJhbGciOiJSUzI1NiJ9...", "tokenType": "Bearer", "expiresIn": 1800 }
404 user_not_found       el usuario no existe o está dado de baja
400 validation_error     userId nulo, no positivo o no entero
```

- **Solo existe con el perfil `dev`** (`@Profile("dev")` en `DevAuthController`). Sin el perfil, la ruta no se registra y responde 404.
- **Emite exactamente el mismo token que el login real.** Usa el mismo `AuthService.issueTokenFor` y el mismo `JwtTokenProvider`, así que lo que se prueba con `dev-login` es lo que va a recibir el sistema con Google.
- **El perfil se activa fuera de `application.yml`:** en la run config de IntelliJ, por línea de comandos o en el compose. Un `java -jar` o un deploy sin perfil nunca lo hereda.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Solo Google real (OAuth Playground) | Solo prueba tu propia cuenta, como cliente. Vence en 1 h y no sirve para scripts ni para la carga |
| Firmar tokens en cada test con una clave de test | Sirve para tests unitarios (`JwtTokenProviderTest` lo hace), pero no contra el sistema levantado: el gateway valida con la clave del auth-service |
| Un token de admin fijo en la configuración | Un token largo y compartido es peor que una puerta trasera explícita: no vence y nadie sabe quién lo usa |
| Proteger `dev-login` con un secreto | Suma configuración para un endpoint que ya está limitado a desarrollo |

## Consecuencias

- **Riesgo:** con el perfil `dev` activo, cualquiera que llegue al auth-service, o al gateway, obtiene el token de cualquier usuario, incluido el admin.
- **Mitigaciones:**
  - `DevAuthControllerWithoutDevProfileTest` verifica que sin el perfil la ruta da 404;
  - al arrancar con `dev`, el auth-service loguea un `WARN` avisando que `dev-login` está activo;
  - el perfil nunca se activa desde `application.yml`.

## Guía de uso

### 1. Activar el perfil `dev`

| Cómo corrés el auth-service | Qué hacer |
|---|---|
| IntelliJ, con la config de `.run/` | Nada: `auth-service` ya trae `-Dspring.profiles.active=dev` |
| Terminal | `./mvnw -pl auth-service spring-boot:run -Dspring-boot.run.profiles=dev` |
| Docker | El compose setea `SPRING_PROFILES_ACTIVE=dev` (ADR-001) |

Para comprobar que está activo, el log del arranque tiene que mostrar `dev-login está activo`.

### 2. Pedir un token

Por el gateway:

```bash
curl -X POST http://localhost:8080/api/auth/dev-login \
     -H "Content-Type: application/json" -d '{"userId": 3}'
```

Usuarios del `AuthSeeder`:

| `userId` | Rol | Organización |
|---|---|---|
| 1 | Cliente | — |
| 2 | Organizador | 1 |
| 3 | Administrador | — |
| 4 | Personal de acceso | — (hasta que exista `AccessStaff`, #19) |
| 5 | Organizador | 2 |
| 6 | Organizador (sin eventos propios) | 1 |

En Postman, la carpeta **Login de desarrollo** de la colección del auth-service pide el token del cliente y lo guarda en `{{token}}`.

### 3. Usarlo

```http
Authorization: Bearer <accessToken>
```

Hasta que el gateway y los servicios validen el JWT (PRs siguientes de la épica #9), los endpoints todavía leen `X-User-Id`. El token ya se puede pedir y revisar, por ejemplo en [jwt.io](https://jwt.io), pero recién lo exigen esos PRs.

### 4. Cuánto dura

- **Vence a los 30 minutos.** Después, el sistema responde 401: se pide otro.
- **Si el auth-service usa clave efímera** (sin `JWT_PRIVATE_KEY_LOCATION`), cada reinicio invalida todos los tokens emitidos antes. Para que sobrevivan, configurá un `.pem` (ver *Firma del JWT* en [`auth-service.md`](../architecture/auth-service.md)).

### 5. Uso correcto

| Hacer | No hacer |
|---|---|
| Usarlo para probar roles, Postman, integración y carga | Usarlo para "probar el login con Google": eso se prueba con la página mínima de login |
| Pedir un token por usuario virtual en la prueba de carga | Pegar tokens en el repo, en issues o en el chat del equipo: aunque vencen, identifican a un usuario |
| Activar el perfil en la run config, la terminal o el compose | Agregar `spring.profiles.active: dev` en `application.yml` |
| Firmar con una clave de test en los tests unitarios | Depender de `dev-login` en un test unitario: no lo necesita |

### 6. Problemas comunes

| Síntoma | Causa |
|---|---|
| `dev-login` da 404 | El perfil `dev` no está activo: revisá el log del arranque |
| Un token que andaba ahora da 401 | Venció (30 min), o se reinició el auth-service con clave efímera |
| `404 user_not_found` | Ese `userId` no existe o está dado de baja |
