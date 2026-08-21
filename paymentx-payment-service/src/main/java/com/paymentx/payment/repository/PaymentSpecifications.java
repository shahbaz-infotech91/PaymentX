package com.paymentx.payment.repository;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.entity.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Each method returns a null-safe Specification (returns null / a
 * conjunction-true predicate when the criterion is absent) so callers
 * can compose only the filters actually supplied, via
 * Specification.allOf/and, without a chain of manual null-checks at the
 * call site.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentSpecifications is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.repository and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentSpecifications PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.repository package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {}

    public static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Payment> hasScheme(PaymentScheme scheme) {
        return (root, query, cb) -> scheme == null ? cb.conjunction() : cb.equal(root.get("scheme"), scheme);
    }

    /**
     * Matches a participant as EITHER the debtor or the creditor - a
     * search for "everything involving participant X" naturally means
     * either side of the transfer, not just one.
     */
    public static Specification<Payment> involvesParticipant(String participantId) {
        return (root, query, cb) -> {
            if (participantId == null || participantId.isBlank()) {
                return cb.conjunction();
            }
            return cb.or(
                    cb.equal(root.get("debtorParticipantId"), participantId),
                    cb.equal(root.get("creditorParticipantId"), participantId));
        };
    }

    public static Specification<Payment> createdAfter(OffsetDateTime from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Payment> createdBefore(OffsetDateTime to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Payment> amountAtLeast(BigDecimal minAmount) {
        return (root, query, cb) -> minAmount == null ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("amount").get("amount"), minAmount);
    }

    public static Specification<Payment> amountAtMost(BigDecimal maxAmount) {
        return (root, query, cb) -> maxAmount == null ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("amount").get("amount"), maxAmount);
    }
}
