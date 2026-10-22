FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY order-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
