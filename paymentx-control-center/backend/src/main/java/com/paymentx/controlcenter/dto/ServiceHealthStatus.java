package com.paymentx.controlcenter.dto;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The result of calling one real PaymentX service's
 * /actuator/health (or /health/liveness, /health/readiness).
 * What it does: carries the real HTTP outcome - status text as
 * reported by that service ("UP"/"DOWN"/etc, or "UNREACHABLE" if the
 * call itself failed), the real HTTP status code, and how long the
 * call took - never a fabricated status. Why it exists: this is what
 * ServiceHealthClient returns for every one of the 9 real service
 * calls; a null/"UNREACHABLE" status here honestly reflects a service
 * that is actually down or unrunning, which callers must be able to
 * tell apart from a genuine "DOWN" health response. How it will
 * communicate with the backend: this IS the backend's real response
 * shape - GET /api/v1/services/{service}/health returns this wrapped
 * in ApiResponse.
 *
 * HINGLISH: Ek real PaymentX service ke /actuator/health (ya
 * /health/liveness, /health/readiness) ko call karne ka result. Ye
 * kya karti hai: real HTTP outcome carry karta hai - us service ne
 * jo status text report kiya ("UP"/"DOWN"/etc, ya "UNREACHABLE" agar
 * call khud fail ho gaya), real HTTP status code, aur call me kitna
 * time laga - kabhi ek fabricated status nahi. Ye dashboard me kyu
 * hai: ye wahi hai jo ServiceHealthClient 9 real service calls me se
 * har ek ke liye return karta hai; yahan ek null/"UNREACHABLE" status
 * honestly ek aisi service reflect karta hai jo actually down ya not-
 * running hai, jise callers ek genuine "DOWN" health response se
 * alag bata paayein. Backend se kaise connect hogi: ye khud backend
 * ka real response shape hai - GET /api/v1/services/{service}/health
 * ise ApiResponse me wrapped return karta hai.
 */
public record ServiceHealthStatus(
        String serviceSlug,
        String serviceName,
        String baseUrl,
        String status,
        Integer httpStatusCode,
        long responseTimeMillis,
        String errorMessage,
        OffsetDateTime checkedAt
) {
}
