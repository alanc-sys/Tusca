FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY rutatur-common/pom.xml rutatur-common/pom.xml
COPY route-match-service/pom.xml route-match-service/pom.xml
COPY rutatur-common/src rutatur-common/src
COPY route-match-service/src route-match-service/src

RUN mvn -N install && \
    mvn -f rutatur-common/pom.xml install -DskipTests && \
    mvn -f route-match-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S rutatur && adduser -S rutatur -G rutatur
USER rutatur

COPY --from=build /workspace/route-match-service/target/route-match-service-*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
