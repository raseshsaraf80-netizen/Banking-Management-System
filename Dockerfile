# Author: Rasesh Saraf
# Multi-stage Dockerfile for SRM Trust Bank Javalin REST API

# Stage 1: Build executable shaded JAR with Maven + JDK 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# Stage 2: Lightweight JRE 17 runtime (glibc-based for sqlite-jdbc native support)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV PORT=7070
ENV BANK_DB_PATH=/app/data/bank.db

RUN mkdir -p /app/data /app/logs /app/statements

COPY --from=build /app/target/banking-system-1.0.0-api.jar /app/app.jar

EXPOSE 7070

CMD ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/app.jar"]

