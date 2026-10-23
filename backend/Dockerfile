# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# pom.xml primero: las dependencias quedan en una capa cacheada mientras el pom no cambie.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Sin tests: usan Testcontainers y necesitan Docker, que no existe dentro del build de la imagen.
RUN mvn -B -q package -DskipTests \
    && mv target/bookstore-inventory-api-*.jar target/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/app.jar app.jar

USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# start_period cubre el arranque de la JVM y Flyway; /books?size=1 también comprueba la conexión a la BD.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null "http://127.0.0.1:8080/books?size=1" || exit 1

# Forma exec: la JVM es el PID 1 y recibe SIGTERM.
ENTRYPOINT ["java", "-jar", "app.jar"]
