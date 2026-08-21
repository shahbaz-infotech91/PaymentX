package com.paymentx.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * WHY this exists alongside Spring Boot Actuator's built-in
 * {@code /actuator/health}: Actuator's format is excellent for
 * infrastructure tooling (Kubernetes probes, Prometheus) but every
 * service in this project already exposes it via
 * {@code management.endpoints.web.exposure.include}. This DTO is for a
 * different purpose - a service-authored, custom health/status endpoint
 * that wants to report business-relevant detail (e.g. "Kafka consumer
 * lag," "last successful DB migration") in a shape the team controls
 * directly, without depending on Actuator's evolving internal format.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * HealthResponse is a record (DTO) in the common module of PaymentX. It lives in package com.paymentx.common.dto and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * HealthResponse PaymentX ke common module ka ek record (DTO) hai. Ye com.paymentx.common.dto package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record HealthResponse(
        String status,
        String serviceName,
        String version,
        Instant checkedAt,
        Map<String, String> details
) {
    public static HealthResponse up(String serviceName, String version) {
        return new HealthResponse("UP", serviceName, version, Instant.now(), Map.of());
    }

    public static HealthResponse down(String serviceName, String version, Map<String, String> details) {
        return new HealthResponse("DOWN", serviceName, version, Instant.now(), details);
    }
}
