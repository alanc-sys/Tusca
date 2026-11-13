FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY rutatur-common/pom.xml rutatur-common/pom.xml
COPY itinerary-service/pom.xml itinerary-service/pom.xml
COPY rutatur-common/src rutatur-common/src
COPY itinerary-service/src itinerary-service/src

RUN mvn -N install && \
    mvn -f rutatur-common/pom.xml install -DskipTests && \
    mvn -f itinerary-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S rutatur && adduser -S rutatur -G rutatur
USER rutatur

COPY --from=build /workspace/itinerary-service/target/itinerary-service-*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
