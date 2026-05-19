ARG APP_VERSION=0.0.1
ARG BUILD_DATE=unknown
ARG SOURCE_URL=https://github.com/mahibnasif/pulseops
ARG VCS_REF=unknown

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src/ src/
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine

ARG APP_VERSION
ARG BUILD_DATE
ARG SOURCE_URL
ARG VCS_REF

LABEL org.opencontainers.image.title="PulseOps backend" \
      org.opencontainers.image.description="PulseOps monitoring and incident-management API" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}"

RUN addgroup -S pulseops && adduser -S pulseops -G pulseops

WORKDIR /app

COPY --from=build --chown=pulseops:pulseops \
    /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER pulseops

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "/app/app.jar"]
