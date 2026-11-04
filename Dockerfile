# RutaTur image factory. One Dockerfile for the whole reactor: the module is selected with
# `--build-arg MODULE=<module>`, so every service shares the same build cache.
#
#   docker build --build-arg MODULE=poi-service -t rutatur/poi-service .
#
# Build from the repository root; `docker compose build` does this automatically.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POMs first so dependency resolution is cached independently of the sources.
COPY pom.xml ./
COPY rutatur-common/pom.xml rutatur-common/
COPY poi-service/pom.xml poi-service/
COPY itinerary-service/pom.xml itinerary-service/
COPY route-match-service/pom.xml route-match-service/
COPY api-gateway/pom.xml api-gateway/
RUN mvn -B -q -DskipTests dependency:go-offline || true

COPY . .

ARG MODULE
RUN test -n "$MODULE" && mvn -B -q -pl "${MODULE}" -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
RUN addgroup -S rutatur && adduser -S rutatur -G rutatur
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/app.jar app.jar
USER rutatur
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
