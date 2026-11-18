# ADR-003: El JWT lo valida solo el api-gateway

- **Estado:** Aceptado. Reemplaza la decisión "Dónde se valida el JWT" del RFC-001, que proponía validarlo también en cada servicio
- **Fecha:** 17/11/2026
- **Relacionado:** [RFC-001](../rfc/RFC-001-auth-modality.md), ADR-001, US-AUTH-03, US-PAY-14, épica #9

## Contexto

El auth-service emite un JWT de TicketYa, firmado con RS256, que lleva el usuario, su rol, sus permisos y su organización (RFC-001). Hay que decidir quién verifica ese token antes de que un servicio aplique `@PreAuthorize`.

Primero se decidió validarlo en el gateway **y** en cada servicio, porque "la red interna sola no alcanza" (US-PAY-14). Al diseñar la configuración común aparecieron tres costos:

- **Cada servicio necesita la clave pública.** Con la clave efímera de desarrollo (cambia en cada reinicio del auth-service), siete servicios tendrían que conseguirla, por ejemplo con un endpoint JWKS y un `kid` en cada token.
- **Cada servicio suma la configuración de resource server:** decoder, validadores de `iss` y `aud`, rutas públicas y `/internal` exceptuadas.
- **Los servicios de otros integrantes del equipo** (ticket y payment) pasarían a exigir token apenas `common` trajera Spring Security.

## Decisión

**Solo el api-gateway valida el JWT.** Los servicios confían en los headers que pone el gateway.

```
Cliente ──Bearer JWT──▶ api-gateway ──X-User-Id, X-User-Role,──▶ servicio
                        · valida firma,       X-User-Permissions,      · arma el usuario con esos headers
                          iss, aud y exp      X-Organization-Id        · @PreAuthorize("hasAuthority('EVENT_MANAGE')")
                        · borra los X-User-*                           · no ve el token
                          que mande el cliente
```

- **En el gateway el token es opcional.** Si viene, tiene que ser válido (si no, `401 unauthorized`) y se convierte en headers. Si no viene, el request pasa sin identidad. Así la lista de rutas públicas no se repite en el gateway.
- **En cada servicio**, un filtro de `common` convierte los `X-User-*` en el usuario de Spring Security: los permisos pasan a ser authorities. Sin headers, el request queda anónimo.
- **Cada endpoint declara lo que pide con `@PreAuthorize`.** Un endpoint protegido responde `401` a un request anónimo y `403` a un usuario sin el permiso. Un endpoint sin anotación es público, igual que hoy con `@RequiresPermission`.
- **Los permisos siguen saliendo del token:** el gateway solo los copia a un header. Ningún servicio llama al auth-service para autorizar.

## Por qué

1. **Un solo lugar valida.** La clave pública, el `iss`, el `aud` y el vencimiento se configuran una vez, en el gateway.
2. **Elimina el problema de distribuir la clave.** Solo el gateway necesita la clave pública, incluso con la clave efímera de desarrollo.
3. **Menos cambios en cada servicio.** Se pasa del `PermissionInterceptor` a `@PreAuthorize` sin cambiar cómo llega la identidad, que siguen siendo headers. Es el mismo criterio que en la decisión de no consultar a auth en cada request: evitar cambios de código en todos los servicios.
4. **El gateway ya es el único punto de entrada.** En el `docker-compose` general solo se publica su puerto (ADR-001 y el diseño de Docker), y ya hoy no reenvía `/internal/**`.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Validar en el gateway y en cada servicio (la decisión anterior) | Es defensa en profundidad, pero trae los tres costos del *Contexto*, que en el alcance del trabajo pesan más que el riesgo que cubre |
| Validar solo en cada servicio, y que el gateway reenvíe el token | El gateway deja de cortar los tokens inválidos, y cada servicio carga con toda la configuración |
| Que los servicios consulten al auth-service en cada request | Es lo que hace la Entrega 1. Acopla todos los servicios al auth y suma una llamada por request |

## Consecuencias

**Se acepta un riesgo:** si alguien llega a un servicio por la red interna, sin pasar por el gateway, puede mandar un `X-User-Permissions` falso y actuar como cualquier usuario. Esto contradice la frase de la US-PAY-14 *"la red interna sola no alcanza"* en lo que respecta a la identidad del usuario.

**Mitigaciones:**
- solo se publica el puerto del gateway; los servicios son accesibles únicamente desde la red de Docker;
- el gateway borra siempre los `X-User-*` que manda el cliente, y un test lo verifica;
- las llamadas a `/internal/**` llevan además el secreto compartido de la US-PAY-14. Lo que esa historia protege, cobrar y reintegrar, no depende solo de la red.

**Otras consecuencias:**
- **El gateway pasa a ser crítico:** un error al copiar o al borrar headers afecta a todos los servicios. Por eso sus tests cubren el borrado de headers falsos y cada caso de token inválido.
- **En los `@WebMvcTest`, la identidad se simula con headers,** igual que en la Entrega 1. No hace falta firmar tokens.

**Evolución:** si el sistema pasara a producción, cada servicio podría validar también el token (defensa en profundidad), con el gateway reenviando el `Authorization`. Como los `@PreAuthorize` leen authorities y no el token, ese cambio queda en la configuración común y no toca los endpoints.
