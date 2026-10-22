# Multi-stage Dockerfile for Order Service
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
COPY order-service/src order-service/src

RUN mvn -pl common-events,order-service clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/order-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
