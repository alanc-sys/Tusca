# Multi-stage build desde la raíz del reactor para resolver rutatur-common
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY rutatur-common/pom.xml rutatur-common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY rutatur-common/src rutatur-common/src
COPY api-gateway/src api-gateway/src

RUN mvn -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S rutatur && adduser -S rutatur -G rutatur
USER rutatur

COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
