# Build stage
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

# Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/apexai-0.0.1-SNAPSHOT.jar app.jar
ENV DOCKER_COMPOSE_ENABLED=false
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/apexai
ENV SPRING_DATASOURCE_USERNAME=admin
ENV SPRING_DATASOURCE_PASSWORD=f1password
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "app.jar"]
