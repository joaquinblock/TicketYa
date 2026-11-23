# 0008: Despliegue con un docker-compose por servicio

- **Fecha:** 2026-11-23
- **Estado:** aceptada
- **Relacionado:** épica #11, [ADR-001](ADR-001-docker-dev-profile.md) (perfil `dev`), [ADR-003](ADR-003-jwt-validated-only-in-gateway.md) (solo el gateway queda expuesto), US-PAY-14

## Contexto
La consigna pide que cada microservicio, cada base y el gateway corran en su propio contenedor, y que todo el sistema se levante con un único `docker-compose.yml` (Entregable 2). Hasta ahora cada servicio tenía un `compose.yaml` con solo su base, que `spring-boot-docker-compose` levanta al correr `spring-boot:run`. Esa dependencia arma el datasource, pero es de desarrollo y no viaja en el jar: con `java -jar` el servicio no arranca.

Restricciones:

- El desarrollo con `spring-boot:run` no puede cambiar.
- El sistema completo son 17 contenedores (8 servicios, 5 MySQL, MongoDB, Redis, RabbitMQ y Mailpit) y tiene que entrar en las máquinas del equipo.
- Ningún servicio salvo el gateway debe quedar expuesto: las rutas `/internal/**` solo las protege el secreto entre servicios.

## Decisión
- **Un `Dockerfile` por servicio** que copia el jar compilado con `./mvnw package -DskipTests` sobre `eclipse-temurin:25-jre`.
- **Un `docker-compose.yml` por servicio** con el servicio y su almacenamiento. El `docker-compose.yml` de la raíz los suma con `include`, junto con `rabbitmq.compose.yaml`, en un solo proyecto y una sola red. Se llama distinto al `compose.yaml` de desarrollo para que `spring-boot:run` no levante también el contenedor del servicio.
- **La configuración entra por variables de entorno:**
  - el datasource (`SPRING_DATASOURCE_*`, `SPRING_MONGODB_URI`, `SPRING_DATA_REDIS_HOST`);
  - las URLs de los otros servicios, por nombre (`http://event-service:8082`);
  - `RABBITMQ_HOST`, `MAIL_HOST` e `INTERNAL_SECRET`;
  - `SPRING_PROFILES_ACTIVE=dev` (ADR-001).
  Tienen valores por defecto iguales a los de desarrollo: sin `.env` el sistema levanta igual. `.env.example` lista las que se pueden cambiar.
- **Solo el gateway publica puerto (8080)**, más las consolas de RabbitMQ (15672) y Mailpit (8025). Los servicios y las bases solo se ven dentro de la red de Docker.
- **Cada servicio espera a que su base esté sana** (`depends_on: condition: service_healthy`). El gateway no espera a nadie: mientras un servicio no levanta, sus rutas responden 503. El healthcheck de cada servicio pide `/actuator/health` con `bash` y `/dev/tcp`, porque la imagen no trae `curl` ni `wget`.
- **Límites de memoria:** 384 MB por servicio con `MaxRAMPercentage=75`, `SerialGC` y `-Xss512k`; 256 MB por MySQL con `innodb-buffer-pool-size=32M` y sin `performance_schema`. Todo junto necesita unos 4 GB para Docker.

## Alternativas consideradas
- **Build multi-etapa (compilar dentro de la imagen):** no depende del paso previo de Maven, pero cada imagen descarga las dependencias y recompila `common`. Son 8 compilaciones más lentas que una sola en la raíz.
- **Un solo `docker-compose.yml` con todo:** sin `include` queda un archivo de 400 líneas que tocan todos los equipos. Por servicio, cada uno es dueño de su parte, y `docker compose up ticket-service` levanta solo lo que ese servicio necesita.
- **Jib o buildpacks:** generan la imagen sin Dockerfile, pero suman un plugin y esconden lo que la cátedra pide ver.
- **Eureka (Service Discovery):** el DNS interno de Docker ya resuelve cada servicio por su nombre, así que un registro aparte no agrega nada con una instancia por servicio.
- **Publicar el puerto de cada servicio:** facilita llamar a `/internal/**` desde Postman, pero deja expuestas las rutas internas (US-PAY-14).

## Consecuencias
- Antes de levantar hay que compilar. Si se olvida, la imagen lleva un jar viejo, o falla con "no hay jar" si nunca se compiló.
- Con `ddl-auto: create`, cada reinicio de un contenedor vuelve a los datos del seeder. Para la demo y la prueba de carga es lo buscado.
- Por lo mismo, en el sistema completo RabbitMQ no tiene volumen: el compose de la raíz lo incluye con `rabbitmq.no-data.compose.yaml`, que anula el volumen con `!reset`. Con un volumen heredado de otra corrida, RabbitMQ falló al leer `.erlang.cookie` (`EACCES`). El exchange y las colas se declaran solos al arrancar. El desarrollo mantiene su volumen.
- Las colecciones de Postman que llaman a `/internal/**` directo al puerto de un servicio no funcionan contra el sistema en Docker. Todo lo de `/api/**` sí, por el gateway.
- El sistema completo y el desarrollo no corren a la vez, porque RabbitMQ usa el mismo puerto en los dos.
- Pendientes:
  - La clave del JWT queda efímera (al reiniciar auth-service, los tokens emitidos dejan de valer) hasta pasarla como Docker secret.
  - Para la prueba de carga, quizás convenga cambiar `SerialGC` y subir los límites.
