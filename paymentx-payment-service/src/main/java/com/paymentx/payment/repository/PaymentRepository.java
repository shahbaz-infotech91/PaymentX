package com.paymentx.payment.repository;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WHY JpaSpecificationExecutor is added here (controller-layer task):
 * GET /payments and GET /payments/search both need dynamic, optional
 * multi-field filtering (status, participant, scheme, date range, amount
 * range) that no fixed set of derived query methods can express cleanly -
 * Specification composition (see PaymentSpecifications) is the standard
 * Spring Data JPA mechanism for this. Purely additive - the three
 * existing methods below are completely unchanged.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentRepository is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.repository and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentRepository PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.repository package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    /**
     * The idempotency check for THIS service - see Payment entity's
     * javadoc for why this is a separate concern from paymentReference
     * uniqueness.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Used by monitoring/ops tooling and the timeout-detection scheduler
     * (Part 5) - "find every payment stuck in PROCESSING or RETRYING" is
     * a recurring operational query, hence the dedicated method rather
     * than composing it ad hoc at each call site.
     */
    List<Payment> findByStatusIn(List<PaymentStatus> statuses);
}
