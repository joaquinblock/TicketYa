# Spring Mail

Lo que aprendimos al mandar el mail de la compra desde un listener de RabbitMQ (#152). Visto con Spring Boot 4.0.9 y Jakarta Mail.

- **JavaMail no tiene timeouts por defecto.** Un SMTP que acepta la conexión y no contesta deja el hilo colgado para siempre. En un listener, eso significa la transacción abierta y el mensaje sin confirmar. Se configuran con `spring.mail.properties.mail.smtp.connectiontimeout`, `timeout` y `writetimeout`, en milisegundos. Ejemplo: `notification-service/src/main/resources/application.yml` y `MailTimeoutTest`.
- **Con `@MockitoBean JavaMailSender` el contexto de test no levanta.** El health de mail de Actuator necesita un `JavaMailSenderImpl` y falla con `'beans' must not be empty`. En el perfil de test va `management.health.mail.enabled: false`. Ejemplo: `notification-service/src/test/resources/application-test.yml`.
