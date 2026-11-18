# ADR-001: El docker-compose corre con el perfil `dev`

- **Estado:** Aceptado
- **Fecha:** 17/11/2026
- **Relacionado:** [RFC-001](../rfc/RFC-001-auth-modality.md), US-AUTH-03, épicas #9 y #11

## Contexto

El login es con Google (RFC-001). Un ID token de Google se obtiene desde un navegador y vence en 1 h, así que no sirve para:

- probar con los usuarios del seeder de cada rol (admin, organizador, personal de acceso y cliente), que tienen emails ficticios;
- las pruebas de integración y las colecciones de Postman;
- la prueba de carga, que necesita cientos de usuarios con token.

Para eso existe `POST /api/auth/dev-login {userId}`, que emite el JWT de TicketYa para un usuario cargado. Solo existe con el perfil `dev` (`@Profile("dev")`), porque permite hacerse pasar por cualquier usuario.

El `docker-compose` general es el entorno en el que el equipo prueba, el docente corre la demo y se hace la prueba de carga. No es un entorno de producción.

## Decisión

Todos los servicios del `docker-compose` general corren con `SPRING_PROFILES_ACTIVE=dev`, así que `dev-login` está disponible a través del gateway.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un perfil `docker` sin `dev-login` | En Docker solo se podría entrar con Google y como cliente: no habría forma de probar los roles ni de hacer la prueba de carga |
| Un compose aparte para pruebas (`docker-compose.dev.yml`) | Duplica la configuración para un solo endpoint de diferencia |

## Consecuencias

- **Riesgo:** cualquiera que llegue al puerto 8080 de la máquina obtiene un token de admin. Es aceptable porque el compose no se expone a internet.
- **Mitigaciones:**
  - un test verifica que sin el perfil `dev` el endpoint no existe;
  - al arrancar con `dev`, el auth-service loguea una advertencia de que `dev-login` está activo;
  - un despliegue real usaría otro perfil, sin `dev-login`.
- **Otros efectos del perfil `dev`:** la trazabilidad propone logs en texto plano en `dev` y JSON en el resto. Como el compose usa `dev` y manda los logs a Loki, ahí el formato JSON se activa por variable (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) y no por el perfil. Lo mismo aplica a cualquier otra configuración atada a `dev`: hay que revisar que tenga sentido en Docker.
