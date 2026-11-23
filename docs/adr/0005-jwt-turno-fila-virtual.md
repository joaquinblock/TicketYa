# ADR-0005: Firma y distribución del turno de la fila virtual

- **Fecha:** 22/11/2026
- **Estado:** Aceptado
- **Relacionado:** US-QUE-01 (#147), US-TKT-21 (#64), ADR-003

## Contexto

queue-service habilita clientes por tandas y debe entregar un turno verificable por ticket-service. La validación no debe sumar una llamada remota por reserva. El JWT de autenticación de auth-service no sirve como turno: tiene otro emisor, otra finalidad y no está ligado a la generación de la entrada de la fila.

## Decisión

queue-service emite turnos firmados con **RS256 y una clave privada propia**. Publica el JWKS en `/.well-known/jwks.json`, que ticket-service consume directamente para validar el token localmente. La implementación de esa validación pertenece a US-TKT-21.

El contrato incluye `iss=ticketya-queue`, `aud=ticket-service`, `sub` con el cliente, `eventId`, `iat` de habilitación, `exp` y `enabledUntil` iguales al fin fijo de la ventana, y `jti` con la generación de ingreso. La aplicación guarda el token emitido con `HSETNX`, de modo que consultas concurrentes devuelvan el mismo token.

La clave privada se monta mediante `QUEUE_JWT_PRIVATE_KEY_LOCATION` en formato PKCS#8; nunca se comparte con auth-service. En desarrollo, la ausencia de esa ruta genera una clave efímera. Para producción, la clave debe ser persistente y la política de rotación debe respetar los turnos todavía vigentes (máximo 10 minutos).

## Alternativas consideradas

| Alternativa | Motivo de descarte |
|---|---|
| Reutilizar la clave privada de auth-service | Mezcla finalidades y amplía el impacto de una filtración; además permitiría emitir JWT de autenticación desde queue-service. |
| Configurar manualmente el PEM público en ticket-service | Acopla el despliegue de ambos servicios a cada cambio de clave. Se eligió el JWKS estándar, como distribución pública y descubrible. |
| Consultar queue-service en cada reserva | Añade latencia y dependencia síncrona justo en el pico de compra, contrario a US-TKT-21. |
| HS256 con secreto compartido | ticket-service necesitaría la clave privada de firma para validar, por lo que también podría emitir turnos. |

## Consecuencias

- ticket-service debe validar firma, algoritmo, emisor, audiencia, expiración, `sub` y `eventId`, y contrastar el cliente con la identidad autenticada. No debe confiar en el JWT de turno como identidad general.
- La clave pública no es secreta; el JWKS se sirve directamente desde queue-service. El gateway sigue validando únicamente los JWT de autenticación según ADR-003.
- El JWKS actual publica una clave activa. Una rotación debe planificarse después de que expiren los turnos emitidos, o deberá ampliarse el soporte para publicar simultáneamente claves anteriores y nuevas.
- Reiniciar con una clave efímera de desarrollo invalida la verificación de turnos emitidos antes del reinicio.
