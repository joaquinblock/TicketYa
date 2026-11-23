# Copia el jar ya compilado: antes, ./mvnw package -DskipTests en la raíz (ver docker-compose.yml de la raíz).
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/event-service-*.jar app.jar
EXPOSE 8082
# El heap sale del límite de memoria del contenedor, no de la memoria de la máquina.
# SerialGC y un stack más chico: con un solo usuario por servicio alcanza, y así entran todos en una máquina
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
