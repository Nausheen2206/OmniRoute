FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY telemetry-simulator/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
