package com.paymentx.controlcenter.dto;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The payload returned by the dashboard-specific health
 * endpoint. What it does: reports this backend's own status, version,
 * and active profile - real, live values (java.lang.Package/Environment),
 * never hardcoded strings. Why it exists: distinct from Spring Boot
 * Actuator's generic /actuator/health (which this module also exposes) -
 * this is the shape the React frontend's status/environment indicator
 * will actually render, kept dashboard-specific so it can grow
 * (Phase 2: real downstream-service reachability) without reshaping
 * Actuator's own contract. How it will communicate with the backend:
 * returned by HealthController, polled by the frontend's
 * useHealthCheck hook via TanStack React Query.
 *
 * HINGLISH: Ye dashboard-specific health endpoint ka payload hai. Ye
 * kya karti hai: is backend ka apna status, version, aur active profile
 * batati hai - real, live values (java.lang.Package/Environment se),
 * kabhi hardcoded strings nahi. Ye dashboard me kyu hai: Spring Boot
 * Actuator ke generic /actuator/health se alag hai (jo ye module bhi
 * expose karta hai) - ye wahi shape hai jo React frontend ka
 * status/environment indicator actually render karega, dashboard-
 * specific rakha gaya hai taaki ye badh sake (Phase 2: real downstream-
 * service reachability) bina Actuator ke apne contract ko reshape kiye.
 * Backend se kaise connect hogi: HealthController ise return karta hai,
 * frontend ka useHealthCheck hook TanStack React Query ke through ise
 * poll karta hai.
 */
public record HealthResponse(
        String status,
        String applicationName,
        String activeProfile,
        String version,
        OffsetDateTime serverTime
) {
}
