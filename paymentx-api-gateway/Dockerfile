# ====================================================================
# ENGLISH
# --------------------------------------------------------------------
# Docker build instructions for the api gateway service - packages the
# compiled Spring Boot jar into a runnable container image, used by
# docker-compose (local dev) and any CI/CD pipeline that builds and
# deploys this service as a container.
#
# ====================================================================
# HINGLISH
# --------------------------------------------------------------------
# api gateway service ke liye Docker build instructions - compiled
# Spring Boot jar ko ek runnable container image me package karta hai,
# docker-compose (local dev) aur koi bhi CI/CD pipeline ise use karta
# hai jo is service ko container ke roop me build/deploy karta hai.
# ====================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY paymentx-common-library/pom.xml paymentx-common-library/
COPY paymentx-api-gateway/pom.xml paymentx-api-gateway/
RUN mvn -pl paymentx-common-library,paymentx-api-gateway -am dependency:go-offline -B

COPY paymentx-common-library/src paymentx-common-library/src
COPY paymentx-api-gateway/src paymentx-api-gateway/src
RUN mvn -pl paymentx-api-gateway -am clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S paymentx && adduser -S paymentx -G paymentx
USER paymentx

COPY --from=build /workspace/paymentx-api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
