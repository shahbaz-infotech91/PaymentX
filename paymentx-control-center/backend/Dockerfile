# ====================================================================
# ENGLISH
# --------------------------------------------------------------------
# Phase 6 - Docker build instructions for the Control Center backend.
# What it does: packages the compiled Spring Boot jar into a runnable
# container image, mirroring the exact multi-stage Maven-build ->
# eclipse-temurin-JRE pattern every other real PaymentX service's own
# Dockerfile already uses (see e.g. paymentx-api-gateway/Dockerfile) -
# non-root user, alpine JRE base, single jar entrypoint. Why every
# module's pom.xml is copied (not just this one's): this backend is
# registered in the ROOT reactor pom.xml's <modules> list alongside the
# other 10 real modules - Maven validates the full reactor structure
# even when building with -pl, so a build context missing any sibling
# module's pom.xml fails the whole build (a real, previously-documented
# bug - see infra/docker-compose.yml's comment on the api-gateway
# service for the exact same trap). Build from the REPO ROOT as
# context: `docker build -f paymentx-control-center/backend/Dockerfile
# -t paymentx-control-center-backend .` (matches how
# paymentx-control-center/docker-compose.yml invokes it).
#
# ====================================================================
# HINGLISH
# --------------------------------------------------------------------
# Phase 6 - Control Center backend ke liye Docker build instructions.
# Ye kya karti hai: compiled Spring Boot jar ko ek runnable container
# image me package karta hai, exactly wahi multi-stage Maven-build ->
# eclipse-temurin-JRE pattern mirror karte hue jo har real PaymentX
# service ka apna Dockerfile already use karta hai (jaise
# paymentx-api-gateway/Dockerfile dekho) - non-root user, alpine JRE
# base, single jar entrypoint. Har module ka pom.xml kyun copy kiya
# gaya (sirf isi ka nahi): ye backend ROOT reactor pom.xml ki
# <modules> list me baaki 10 real modules ke saath registered hai -
# Maven poori reactor structure validate karta hai chahe -pl se build
# ho, isliye ek build context jisme kisi sibling module ka pom.xml
# missing ho poora build fail kar deta hai (ek real, pehle se
# documented bug - infra/docker-compose.yml ka api-gateway service
# comment dekho, exactly wahi trap). REPO ROOT ko context maan kar
# build karo: `docker build -f
# paymentx-control-center/backend/Dockerfile -t
# paymentx-control-center-backend .` (matches ki
# paymentx-control-center/docker-compose.yml ise kaise invoke karta
# hai).
# ====================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY paymentx-common/pom.xml paymentx-common/
COPY paymentx-common-library/pom.xml paymentx-common-library/
COPY paymentx-api-gateway/pom.xml paymentx-api-gateway/
COPY paymentx-auth-service/pom.xml paymentx-auth-service/
COPY paymentx-validation-service/pom.xml paymentx-validation-service/
COPY paymentx-payment-service/pom.xml paymentx-payment-service/
COPY paymentx-routing-service/pom.xml paymentx-routing-service/
COPY paymentx-audit-service/pom.xml paymentx-audit-service/
COPY paymentx-notification-service/pom.xml paymentx-notification-service/
COPY paymentx-reconciliation-service/pom.xml paymentx-reconciliation-service/
COPY paymentx-reporting-service/pom.xml paymentx-reporting-service/
COPY paymentx-control-center/backend/pom.xml paymentx-control-center/backend/
RUN mvn -pl paymentx-control-center/backend dependency:go-offline -B

COPY paymentx-control-center/backend/src paymentx-control-center/backend/src
RUN mvn -pl paymentx-control-center/backend clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S paymentx && adduser -S paymentx -G paymentx
USER paymentx

COPY --from=build /workspace/paymentx-control-center/backend/target/*.jar app.jar

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]
