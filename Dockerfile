# Author: Rasesh Saraf
# Multi-stage Dockerfile for Meridian Trust Bank JDK HttpServer API

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/libs

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV PORT=7070
ENV BANK_DB_PATH=/app/data/bank.db

RUN mkdir -p /app/data /app/logs /app/statements

COPY --from=build /app/target/banking-system-1.0.0.jar /app/app.jar
COPY --from=build /app/target/libs /app/libs

EXPOSE 7070

CMD ["java", "-Djava.net.preferIPv4Stack=true", "-cp", "/app/app.jar:/app/libs/*", "com.bank.api.ApiServer"]

