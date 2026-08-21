package com.paymentx.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Standard success/error envelope for every REST response across the
 * platform. WHY generic {@code <T>} rather than separate success/error
 * response classes: a single shape means every service's controller
 * advice and every client's response-parsing code handles ONE structure,
 * checking {@code success} to branch - not two different JSON shapes
 * depending on outcome.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ApiResponse is a record (DTO) in the common module of PaymentX. It lives in package com.paymentx.common.dto and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ApiResponse PaymentX ke common module ka ek record (DTO) hai. Ye com.paymentx.common.dto package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
