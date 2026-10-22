# Multi-stage Dockerfile for Gateway Service
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

COPY pom.xml .
COPY common-events/pom.xml common-events/
COPY order-service/pom.xml order-service/
COPY warehouse-service/pom.xml warehouse-service/
COPY payment-service/pom.xml payment-service/
COPY gateway/pom.xml gateway/
COPY telemetry-simulator/pom.xml telemetry-simulator/
COPY fleet-routing/pom.xml fleet-routing/
COPY desktop-client/pom.xml desktop-client/

COPY common-events/src common-events/src
COPY gateway/src gateway/src

RUN mvn -pl common-events,gateway clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
