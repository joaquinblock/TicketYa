# Copia el jar ya compilado: antes, ./mvnw package -DskipTests en la raíz (ver docker-compose.yml de la raíz).
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/payment-service-*.jar app.jar
EXPOSE 8084
# El heap sale del límite de memoria del contenedor, no de la memoria de la máquina
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
