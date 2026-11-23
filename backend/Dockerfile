# ---- Etapa 1: compilación ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Etapa 2: ejecución ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S tutofast && adduser -S tutofast -G tutofast
WORKDIR /app
COPY --from=build /app/target/tutofast-backend.jar app.jar
USER tutofast
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -jar app.jar"]
