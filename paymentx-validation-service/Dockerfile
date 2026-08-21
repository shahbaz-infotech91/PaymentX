# ====================================================================
# ENGLISH
# --------------------------------------------------------------------
# Docker build instructions for the validation service - packages the
# compiled Spring Boot jar into a runnable container image, used by
# docker-compose (local dev) and any CI/CD pipeline that builds and
# deploys this service as a container.
#
# ====================================================================
# HINGLISH
# --------------------------------------------------------------------
# validation service ke liye Docker build instructions - compiled
# Spring Boot jar ko ek runnable container image me package karta hai,
# docker-compose (local dev) aur koi bhi CI/CD pipeline ise use karta
# hai jo is service ko container ke roop me build/deploy karta hai.
# ====================================================================

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY paymentx-common/pom.xml paymentx-common/
COPY paymentx-validation-service/pom.xml paymentx-validation-service/
RUN mvn -pl paymentx-common,paymentx-validation-service -am dependency:go-offline -B

COPY paymentx-common/src paymentx-common/src
COPY paymentx-validation-service/src paymentx-validation-service/src
RUN mvn -pl paymentx-validation-service -am clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S paymentx && adduser -S paymentx -G paymentx
USER paymentx

COPY --from=build /workspace/paymentx-validation-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
