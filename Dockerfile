# syntax=docker/dockerfile:1
# SPDX-License-Identifier: BUSL-1.1
# Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
#
# ThunderVox Server image: the Gradle wrapper builds the boot jar on a JDK, the runtime stage carries only the JRE
# and the jar. Published as ghcr.io/beiroun/thundervox-server:<version> by CI on a tagged release.

ARG APP_VERSION=0.0.0-dev

FROM eclipse-temurin:25-jdk AS build
ARG APP_VERSION
WORKDIR /workspace

# Dependency resolution is cached separately from the sources: a code change does not re-download the world
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --quiet > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon -PappVersion="${APP_VERSION}" bootJar

FROM eclipse-temurin:25-jre
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
