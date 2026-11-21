# RabbitMQ con Spring Boot 4

Lo que aprendimos al conectar ticket-service y notification-service a RabbitMQ (#151, [ADR 0004](../adr/0004-rabbitmq-para-avisos-entre-servicios.md)).

- **Spring AMQP no declara nada al arrancar.** `RabbitAdmin` declara exchanges, colas y bindings recién al abrir la primera conexión (al publicar o al consultar el health). Para declararlos al arrancar se llama a `AmqpAdmin.initialize()` y se captura `AmqpException`, así sin broker el servicio arranca igual. Ejemplo: `ticket-service/.../config/RabbitConfig.java`.
- **Con Jackson 3 el convertidor es `JacksonJsonMessageConverter`.** `Jackson2JsonMessageConverter` es el de Jackson 2. Conviene pasarle el `JsonMapper` de Boot para usar la misma configuración que la API. La autoconfiguración usa el bean `MessageConverter` del contexto en el `RabbitTemplate` y en los listeners.
- **Por defecto el health no dice qué componente está caído.** Sin `management.endpoint.health.show-components: always`, `/actuator/health` solo muestra `DOWN`. Con esa propiedad se ve `rabbit: DOWN` sin exponer los detalles.
- **Sin broker el health general da `DOWN` 503.** El servicio sigue atendiendo, pero cualquier `healthcheck` que use ese endpoint lo va a dar por muerto.
- **El usuario `guest` de RabbitMQ solo entra desde localhost.** No sirve cuando los servicios corren en contenedores, por eso el compose crea `ticketya` / `ticketya`.
