FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY fleet-routing/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
