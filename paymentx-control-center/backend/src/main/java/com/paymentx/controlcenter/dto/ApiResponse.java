package com.paymentx.controlcenter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The single response envelope every Control Center backend
 * endpoint returns. What it does: wraps either real payload data
 * (success=true) or an ErrorResponse (success=false) with a server
 * timestamp, so every frontend API call has one predictable shape to
 * parse. Why it exists: matches the ApiResponse<T> convention already
 * established across every existing PaymentX service (see e.g.
 * paymentx-common's ApiResponse) - Phase 1 does not depend on that
 * shared library (independence requirement), so this is a small,
 * self-contained re-implementation of the same, already-proven shape.
 * How it will communicate with the backend: this IS the backend's
 * outgoing shape - the React api/ layer (src/api/axiosClient.ts) unwraps
 * this envelope on every response.
 *
 * HINGLISH: Ye har Control Center backend endpoint ka ek hi response
 * envelope hai. Ye kya karti hai: real payload data (success=true) ya
 * ek ErrorResponse (success=false) ko ek server timestamp ke saath wrap
 * karti hai, taaki har frontend API call ka ek predictable shape ho jo
 * parse karna aasan ho. Ye dashboard me kyu hai: existing PaymentX
 * services me already established ApiResponse<T> convention se match
 * karta hai - Phase 1 us shared library par depend nahi karta
 * (independence requirement), isliye ye same, already-proven shape ka
 * ek chhota, self-contained re-implementation hai. Backend se kaise
 * connect hogi: yehi backend ka outgoing shape hai - React ka api/ layer
 * (src/api/axiosClient.ts) har response par is envelope ko unwrap karta
 * hai.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, OffsetDateTime.now());
    }
}
