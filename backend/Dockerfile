# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp verify

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/tutor-base-backend-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
