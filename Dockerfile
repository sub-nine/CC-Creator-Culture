# syntax=docker/dockerfile:1

ARG JAR_FILE=.image/app.jar

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699 AS runtime-base
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring

FROM runtime-base AS service
ARG JAR_FILE
COPY --chown=spring:spring ${JAR_FILE} /app/app.jar

USER spring:spring
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM service AS config-server
USER root
COPY --chown=spring:spring config-repo /config-repo
USER spring:spring
