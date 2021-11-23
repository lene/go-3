# docker build -t registry.gitlab.com/lilacashes/go-3/server .
# docker run [--net=host] -t registry.gitlab.com/lilacashes/go-3/server:latest
FROM hseeberger/scala-sbt:8u222_1.3.5_2.13.1 AS builder
WORKDIR /go-3
COPY . /go-3
RUN sbt universal:packageBin

FROM openjdk:16

ARG version=0.6.2

RUN microdnf install --nodocs unzip && microdnf clean all
WORKDIR /go-3
COPY --from=builder /go-3/target/universal/go-3d-${version}.zip ./
RUN useradd go-3d
RUN chown -R go-3d .
USER go-3d
RUN ls -al
RUN unzip go-3d-${version}.zip
RUN mv go-3d-${version}/??? . && rm -r go-3d-*.*.*

ENV SAVE_DIR saves
ENV PORT 6030
RUN mkdir -p "${SAVE_DIR}"
EXPOSE ${PORT}
ENTRYPOINT ./bin/runner --server --port "${PORT}" --save-dir "${SAVE_DIR}"
HEALTHCHECK CMD curl --fail http://localhost:${PORT}/health