FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY warehouse-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
