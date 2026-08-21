package com.paymentx.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A reversal is always operator/system-initiated with a documented reason -
 * unlike a customer-initiated return, this is why reason is mandatory here
 * (not optional as it might be on some other action). requestedBy will be
 * replaced by an authenticated principal once Auth Service exists (see
 * JpaAuditingConfig's javadoc for the same interim-state reasoning) -
 * captured explicitly on the request for now so the audit trail (Part 4)
 * has a real value to record rather than a hardcoded placeholder baked
 * into service logic.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReversalRequest is a record (DTO) in the payment module of PaymentX. It lives in package com.paymentx.payment.dto and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReversalRequest PaymentX ke payment module ka ek record (DTO) hai. Ye com.paymentx.payment.dto package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record ReversalRequest(
        @NotBlank(message = "reason is required for a payment reversal")
        @Size(max = 512)
        String reason,

        @NotBlank(message = "requestedBy is required")
        String requestedBy
) {
}
