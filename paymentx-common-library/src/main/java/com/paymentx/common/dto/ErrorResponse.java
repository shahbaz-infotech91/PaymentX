package com.paymentx.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * WHY {@code errorCode} is a String, not an enum: error codes are
 * defined per-service (see {@link com.paymentx.common.constant.ErrorCodes}
 * for the cross-cutting subset) - a shared library enum would need a new
 * release every time any service adds a domain-specific error code,
 * defeating the point of decoupling services. String codes with a
 * documented naming convention (e.g. {@code VALIDATION_001}) achieve the
 * same lookup-ability without that coupling - the same principle already
 * applied to {@code PaymentXException.errorCode} when it was first
 * designed in Payment Service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ErrorResponse is a record (DTO) in the common module of PaymentX. It lives in package com.paymentx.common.dto and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ErrorResponse PaymentX ke common module ka ek record (DTO) hai. Ye com.paymentx.common.dto package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ErrorResponse(
        String errorCode,
        String message,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors
) {
    public static ErrorResponse of(String errorCode, String message, String path) {
        return new ErrorResponse(errorCode, message, path, Instant.now(), null);
    }

    public static ErrorResponse withFieldErrors(String errorCode, String message, String path,
                                                 List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode, message, path, Instant.now(), fieldErrors);
    }

    public record FieldError(String field, String rejectedValue, String reason) {
    }
}
