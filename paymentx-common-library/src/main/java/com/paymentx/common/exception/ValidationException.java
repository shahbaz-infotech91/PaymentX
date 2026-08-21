package com.paymentx.common.exception;

import lombok.Getter;

import java.util.List;

/**
 * WHY this carries a {@code List<String>} of field-level messages rather
 * than just the base {@code message}: a validation failure is frequently
 * MULTIPLE simultaneous problems (three required fields missing at once).
 * A global exception handler can render all of them in one response
 * (via {@link com.paymentx.common.dto.ErrorResponse#withFieldErrors}
 * instead of forcing a caller to fix and resubmit one error at a time.
 */
@Getter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationException is a exception in the common module of PaymentX. It lives in package com.paymentx.common.exception and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationException PaymentX ke common module ka ek exception hai. Ye com.paymentx.common.exception package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationException extends PaymentXException {

    private final List<String> fieldErrors;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, false);
        this.fieldErrors = List.of();
    }

    public ValidationException(String message, List<String> fieldErrors) {
        super("VALIDATION_ERROR", message, false);
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }
}
