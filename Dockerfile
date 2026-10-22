# syntax=docker/dockerfile:1.28.0@sha256:bb22d9815c728170f72750f4e5b0d672e06176142e1d602c7e66c050100b7e5b
# SPDX-License-Identifier: BUSL-1.1
# Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
#
# ThunderVox Server image: the Gradle wrapper builds the boot jar on a JDK, the runtime stage carries only the JRE
# and the jar. Published as ghcr.io/beiroun/thundervox-server:<version> by CI on a tagged release.

ARG APP_VERSION=0.0.0-dev
# Base images pinned by exact tag and digest (rule of 2026-10-22): a bump changes both, after checking that the tag
# resolves and the digest is downloadable.
ARG JDK_IMAGE=eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e
ARG JRE_IMAGE=eclipse-temurin:25.0.4.1_1-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5

FROM ${JDK_IMAGE} AS build
ARG APP_VERSION
WORKDIR /workspace

# Dependency resolution is cached separately from the sources: a code change does not re-download the world
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --quiet > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon -PappVersion="${APP_VERSION}" bootJar

FROM ${JRE_IMAGE}
ARG APP_VERSION
LABEL org.opencontainers.image.title="ThunderVox Server" \
      org.opencontainers.image.description="Provisioning server of the ThunderVox SIP endpoint platform" \
      org.opencontainers.image.source="https://github.com/beiroun/thundervox-server" \
      org.opencontainers.image.licenses="BUSL-1.1" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN groupadd --system thundervox && useradd --system --gid thundervox --no-create-home --shell /usr/sbin/nologin thundervox
COPY --from=build /workspace/build/libs/thundervox-server.jar /app/thundervox-server.jar

USER thundervox
# Host-networked in the compose deployment; the port is informational
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/thundervox-server.jar"]
