package com.paymentx.payment.repository;

import com.paymentx.payment.entity.PaymentOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentOutboxRepository is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.repository and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentOutboxRepository PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.repository package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, UUID> {

    /**
     * WHY native SQL with FOR UPDATE SKIP LOCKED, not a JPQL/derived query:
     * JPQL has no portable way to express SKIP LOCKED (it's a Postgres/
     * Oracle-specific row-locking extension, not part of the JPA spec).
     * This is the exact mechanism that makes it safe to run the outbox
     * scheduler (Part 5) on MULTIPLE Payment Service replicas
     * simultaneously: each replica's poll grabs a disjoint batch of rows -
     * rows already locked by another replica's in-flight transaction are
     * silently skipped rather than blocking this query. Without SKIP
     * LOCKED, concurrent replicas would either (a) block each other,
     * serializing all outbox draining through one replica at a time, or
     * (b) both read and attempt to publish the SAME row.
     *
     * MUST be called from within a @Transactional method - the row lock is
     * only held for the duration of the enclosing transaction, and the
     * calling service is responsible for updating status to PUBLISHED
     * (releasing the lock via commit) before that transaction ends.
     */
    @Query(value = "SELECT * FROM payment_outbox WHERE status = 'PENDING' " +
            "ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<PaymentOutbox> findBatchForPublishing(@Param("batchSize") int batchSize);
}
