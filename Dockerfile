FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

RUN apk add --no-cache unzip wget

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
COPY data/node_catalog.json data/seed_workflows.json data/

RUN ./mvnw -B -ntp package

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

RUN addgroup -S -g 10001 relayflow \
    && adduser -S -D -H -u 10001 -G relayflow relayflow

COPY --from=build --chown=10001:10001 /workspace/target/relayflow-*.jar /app/relayflow.jar

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=12 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/relayflow.jar"]
