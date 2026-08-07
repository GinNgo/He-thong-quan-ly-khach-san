FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/mvnw backend/mvnw.cmd ./
COPY backend/.mvn .mvn
RUN chmod +x mvnw && ./mvnw -DskipTests dependency:go-offline

COPY backend/src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
