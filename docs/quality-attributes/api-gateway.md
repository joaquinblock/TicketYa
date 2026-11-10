# Punto de entrada único

> **Enunciado:** "Los clientes externos acceden al sistema a través de un API Gateway."

## Cómo lo definimos

El gateway tenía que ser el único lugar que conocen los clientes, sin lógica de negocio. La pregunta principal fue qué rutas exponer, porque los servicios también tienen operaciones que solo deben llamarse entre ellos.

## Qué elegimos

**Spring Cloud Gateway en su variante WebMVC**, en el puerto 8080. Todo el sistema usa MVC, así que el gateway sigue el mismo modelo y no suma programación reactiva.

- **Ruteo por prefijo de ruta.** Cada servicio declara qué caminos atiende, por ejemplo `/api/orders/**` para ticket-service.
- **URLs por variable de entorno**, como `TICKET_SERVICE_URL`, con `localhost` por defecto. Así la misma configuración sirve en local y en contenedores.
- **Swagger unificado.** Cada servicio publica su documentación bajo `/docs/<servicio>`.

## Protección de las rutas internas

Algunas operaciones solo deben llamarlas otros servicios, nunca un cliente: por ejemplo, cobrar (`POST /internal/payments`) o reintegrar (`POST /internal/refunds`). Si un cliente pudiera llamarlas, podría pedirse un reintegro a sí mismo.

Por eso todas van bajo el prefijo `/internal/**`, separadas de la API pública en `/api/**`. La protección se construye por etapas:

| Etapa | Qué protege | Qué queda sin cubrir |
|---|---|---|
| **Entrega 1:** el gateway no tiene ninguna ruta hacia `/internal/**`, así que responde 404 | El acceso de los clientes a través del punto de entrada | El puerto de cada servicio sigue accesible en local, y quien llegue directo puede llamar esas rutas |
| **Entrega 2:** el `docker-compose` publica solo el puerto del gateway | El acceso desde fuera de la red de Docker | Cualquier contenedor de esa red puede llamarlas |
| **Entrega 2:** credencial de servicio en las llamadas internas, y sin ella 401 | El acceso desde dentro de la red | |

La red sola no alcanza, porque un contenedor comprometido tendría acceso a todas las rutas internas. El mecanismo propuesto es un secreto compartido en un header, cargado por variable de entorno, y se confirma con la épica de seguridad.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Spring Cloud Gateway reactivo | Agrega otro modelo de programación sin que la carga prevista lo requiera |
| Exponer los servicios directamente | El enunciado pide un punto de entrada único |
| Bloquear ruta por ruta en el gateway | Una ruta nueva quedaría expuesta si alguien se olvida de bloquearla. Con rutas explícitas, lo que no está declarado no se reenvía |

## Estado

- **Implementado:** ruteo de todos los servicios, documentación unificada y rutas internas fuera del gateway, con `InternalRoutesTest` confirmando el 404.
- **Pendiente (Entrega 2):** validar el JWT en el gateway, publicar solo su puerto y autenticar las llamadas entre servicios.

## Dónde verlo

Las rutas están en `api-gateway/src/main/resources/application.yml`.
