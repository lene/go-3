# docker build -t ghcr.io/lene/go-3/server .
# docker run [--net=host] -t ghcr.io/lene/go-3/server:latest

FROM sbtscala/scala-sbt:eclipse-temurin-25.0.5_8_1.13.1_3.10.1 AS builder
ARG version=0.7.58

WORKDIR /go-3
COPY . /go-3
RUN ci/sbt-retry.sh "Universal / packageBin"
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
RUN unzip -oq /go-3/target/universal/go-3d-${version}.zip
RUN mv go-3d-${version}/??? . && rm -r go-3d-*.*.* target

FROM eclipse-temurin:25-jre@sha256:945247f9d092703e0c6be92243ef3c51640229e23ab34d353c8dcdc73e6cd4e6
ARG version=0.7.58
ENV SAVE_DIR=saves
ENV PORT=6030

WORKDIR /go-3
RUN apt-get update && apt-get upgrade -y && apt-get install -y --no-install-recommends jq curl time && rm -rf /var/lib/apt/lists/* && useradd go-3d && chown -R go-3d . && mkdir -p "${SAVE_DIR}"
USER go-3d
COPY --from=builder /go-3/bin /go-3/bin/
COPY --from=builder /go-3/lib /go-3/lib/

EXPOSE ${PORT}
ENTRYPOINT ["/bin/sh", "-c", "exec ./bin/runner --server --port $PORT --save-dir $SAVE_DIR"]
HEALTHCHECK CMD curl --fail http://localhost:${PORT}/health