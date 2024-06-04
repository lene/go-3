# docker build -t registry.gitlab.com/lilacashes/go-3/server .
# docker run [--net=host] -t registry.gitlab.com/lilacashes/go-3/server:latest

FROM hseeberger/scala-sbt:17.0.2_1.6.2_3.1.1 AS builder
ARG version=0.7.11

WORKDIR /go-3
COPY . /go-3
RUN sbt "Universal / packageBin"
RUN unzip -oq /go-3/target/universal/go-3d-${version}.zip
RUN mv go-3d-${version}/??? . && rm -r go-3d-*.*.* target

FROM openjdk:21
ARG version=0.7.11
ENV SAVE_DIR saves
ENV PORT 6030

RUN microdnf upgrade
WORKDIR /go-3
RUN useradd go-3d && chown -R go-3d . && microdnf install jq curl time && mkdir -p "${SAVE_DIR}"
USER go-3d
COPY --from=builder /go-3/bin /go-3/bin/
COPY --from=builder /go-3/lib /go-3/lib/

EXPOSE ${PORT}
ENTRYPOINT ./bin/runner --server --port "${PORT}" --save-dir "${SAVE_DIR}"
HEALTHCHECK CMD curl --fail http://localhost:${PORT}/health