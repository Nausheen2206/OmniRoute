FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY fleet-routing-streams/target/*.jar app.jar
EXPOSE 8084
VOLUME /data/kafka-streams
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
