# syntax=docker/dockerfile:1.2
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN --mount=type=secret,id=sentry_auth_token,dst=/etc/secrets/sentry_auth_token \
    export SENTRY_AUTH_TOKEN=$(cat /etc/secrets/sentry_auth_token 2>/dev/null || true) && \
    mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/Gendaz-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Djava.net.preferIPv4Stack=true -Djava.net.preferIPv6Addresses=false ${JAVA_OPTS:--Xmx350m -Xms150m -XX:+UseG1GC -XX:MaxGCPauseMillis=200} -jar /app/app.jar"]

