# syntax=docker/dockerfile:1

ARG JAR_FILE=.image/app.jar

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699 AS runtime-base
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring \
    && mkdir -p /app/certs \
    && chmod 755 /app/certs

FROM runtime-base AS service
ARG JAR_FILE
COPY --chown=spring:spring ${JAR_FILE} /app/app.jar

# Public RDS CA, downloaded and checksum-verified by BuildKit at build time.
ADD --chown=spring:spring --chmod=644 --checksum=sha256:913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229 \
    https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem /app/certs/rds-ap-northeast-2-bundle.pem

USER spring:spring
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM service AS config-server
ENV XDG_CONFIG_HOME=/tmp/jgit
