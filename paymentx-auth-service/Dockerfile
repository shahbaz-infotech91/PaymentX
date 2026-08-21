# ====================================================================
# ENGLISH
# --------------------------------------------------------------------
# Docker build instructions for the auth service - packages the
# compiled Spring Boot jar into a runnable container image, used by
# docker-compose (local dev) and any CI/CD pipeline that builds and
# deploys this service as a container.
#
# ====================================================================
# HINGLISH
# --------------------------------------------------------------------
# auth service ke liye Docker build instructions - compiled
# Spring Boot jar ko ek runnable container image me package karta hai,
# docker-compose (local dev) aur koi bhi CI/CD pipeline ise use karta
# hai jo is service ko container ke roop me build/deploy karta hai.
# ====================================================================

# --- Stage 1: Build ---
# WHY multi-stage: the build stage needs the full Maven + JDK toolchain
# (~600MB+), but the RUNTIME container should never ship that. Multi-stage
# builds let us throw away everything except the final JAR, cutting image
# size from ~700MB to ~180MB. Smaller images = faster deploys, smaller
# attack surface (fewer packages = fewer CVEs to patch), lower registry cost.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy parent POM and common module first, and build them in isolation.
# WHY this ordering matters for Docker layer caching:
# Docker caches each instruction as a layer. If we copied the ENTIRE repo
# and ran `mvn install` in one shot, ANY code change (even a one-line fix
# in auth-service) would invalidate the cache and force Maven to
# re-download every dependency from scratch on every build. By copying
# pom.xml files first and running a dependency-only resolve, dependency
# downloads get cached and reused across builds unless the POMs themselves
# change.
COPY pom.xml .
COPY paymentx-common/pom.xml paymentx-common/
COPY paymentx-auth-service/pom.xml paymentx-auth-service/
RUN mvn -pl paymentx-common,paymentx-auth-service -am dependency:go-offline -B

COPY paymentx-common/src paymentx-common/src
COPY paymentx-auth-service/src paymentx-auth-service/src
RUN mvn -pl paymentx-auth-service -am clean package -DskipTests -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root - a container running as root that gets compromised
# gives an attacker root inside the container. This is a standard PCI-DSS
# / SOC2 audit finding if missed.
RUN addgroup -S paymentx && adduser -S paymentx -G paymentx
USER paymentx

COPY --from=build /workspace/paymentx-auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
