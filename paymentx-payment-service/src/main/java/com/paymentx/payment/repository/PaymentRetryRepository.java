package com.paymentx.payment.repository;

import com.paymentx.payment.entity.PaymentRetry;
import com.paymentx.payment.entity.RetryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentRetryRepository is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.repository and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentRetryRepository PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.repository package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentRetryRepository extends JpaRepository<PaymentRetry, UUID> {

    // WAS findByPaymentIdOrderByRetryAttemptAsc - the retryAttempt field
    // was renamed to currentRetry when PaymentRetry was updated to match
    // the requested naming (currentRetry/maxRetry/nextRetryTime), but this
    // method-name-derived query was missed at the time. Spring Data JPA
    // parses "OrderByRetryAttemptAsc" against the entity's actual fields
    // at STARTUP (not compile time) - a stale field reference here doesn't
    // fail the build, it fails application context initialization instead,
    // which is exactly the UnsatisfiedDependencyException seen at runtime.
    List<PaymentRetry> findByPaymentIdOrderByCurrentRetryAsc(UUID paymentId);

    /**
     * The retry scheduler's core query (Part 5): every SCHEDULED retry
     * whose backoff window has elapsed. Matches the composite index
     * idx_payment_retry_status_next_retry created in the Liquibase
     * changeset - this exact WHERE clause shape is what that index exists
     * to serve.
     */
    @Query("SELECT r FROM PaymentRetry r WHERE r.status = :status AND r.nextRetryTime <= :now")
    List<PaymentRetry> findDueRetries(@Param("status") RetryStatus status, @Param("now") OffsetDateTime now);
}
