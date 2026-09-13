# syntax=docker/dockerfile:1
# Flyway Community CLI. SQL is copied at build so the artifact is pinned to RELEASE_SHA.
FROM flyway/flyway:11.7.2@sha256:8ace7d9825bb3ad1d6e14ee27b3a830b638ac841ba424b99b2d92aa65a99d484
ARG RELEASE_SHA=unknown
ENV RELEASE_SHA=${RELEASE_SHA}
LABEL org.opencontainers.image.revision=${RELEASE_SHA}
COPY database/migrations /flyway/sql
COPY database/scripts/migrate.sh /flyway/scripts/migrate.sh
# Public RDS CA, downloaded and checksum-verified by BuildKit at build time.
ADD --chmod=644 --checksum=sha256:913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229 \
    https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem /certs/rds-global-bundle.pem
USER root
RUN chmod 755 /flyway/scripts/migrate.sh && chmod -R a+rX /flyway/sql /certs
ENTRYPOINT ["/flyway/scripts/migrate.sh"]
