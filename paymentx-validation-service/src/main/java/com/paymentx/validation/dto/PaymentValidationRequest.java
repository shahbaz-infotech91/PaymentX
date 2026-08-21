package com.paymentx.validation.dto;

import com.paymentx.validation.entity.Scheme;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * This is VALIDATION LAYER #1: structural/schema validation.
 *
 * WHY use Jakarta Bean Validation annotations here instead of writing manual
 * "if (amount == null) throw ..." checks in the service layer:
 *   1. Declarative - the constraint IS the documentation. Anyone reading
 *      this class immediately knows amount must be positive, currency must
 *      be a 3-letter ISO code, without reading service logic.
 *   2. Enforced automatically at the controller boundary via @Valid -
 *      malformed requests are rejected with a 400 BEFORE any business logic
 *      (blacklist checks, DB writes) even runs. This matters for payments:
 *      you never want to do a blacklist DB lookup for a request that's
     *      going to fail schema validation anyway.
 *
 * record, not a class: this DTO is purely a data carrier with no identity
 * or mutable state after construction - a Java record is the correct,
 * modern choice (immutable, auto-generated equals/hashCode/toString).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentValidationRequest is a record (DTO) in the validation module of PaymentX. It lives in package com.paymentx.validation.dto and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentValidationRequest PaymentX ke validation module ka ek record (DTO) hai. Ye com.paymentx.validation.dto package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PaymentValidationRequest(

        @NotBlank(message = "paymentReference is required")
        String paymentReference,

        @NotNull(message = "scheme is required")
        Scheme scheme,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code")
        String currency,

        @NotBlank(message = "debtorAccount is required")
        String debtorAccount,

        @NotBlank(message = "debtorBankId is required")
        String debtorBankId,

        @NotBlank(message = "creditorAccount is required")
        String creditorAccount,

        @NotBlank(message = "creditorBankId is required")
        String creditorBankId
) {
}
