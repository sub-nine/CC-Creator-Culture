# syntax=docker/dockerfile:1
FROM postgres:17.11-alpine@sha256:18cfe3ef5e6815560c98237d6216d1e5119702fb0f3894c8785dd58b8bbe5d73
ARG RELEASE_SHA=unknown
ENV RELEASE_SHA=${RELEASE_SHA}
LABEL org.opencontainers.image.revision=${RELEASE_SHA}
COPY database/scripts/bootstrap-roles.sh /scripts/bootstrap-roles.sh
# Public RDS CA, downloaded and checksum-verified by BuildKit at build time.
ADD --chmod=644 --checksum=sha256:913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229 \
    https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem /certs/rds-global-bundle.pem
COPY seed/scripts/seed.sh /scripts/seed.sh
COPY seed/sql /seed/sql
COPY seed/fixtures.env /seed/fixtures.env
RUN chmod 755 /scripts/bootstrap-roles.sh /scripts/seed.sh && chmod -R a+rX /seed /certs
ENTRYPOINT ["/scripts/seed.sh"]
