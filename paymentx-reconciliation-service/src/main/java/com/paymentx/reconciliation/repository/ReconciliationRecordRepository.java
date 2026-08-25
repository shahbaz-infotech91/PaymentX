package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.entity.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationRecordRepository is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.repository and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationRecordRepository PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.repository package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID>, JpaSpecificationExecutor<ReconciliationRecord> {

    List<ReconciliationRecord> findByBatchId(UUID batchId);

    boolean existsByPaymentIdAndBatchId(String paymentId, UUID batchId);

    // Phase 4.6.0 - the real paymentReference -> batchId bridge: referenceId is populated from
    // the same "paymentReference"/"reference" payload field every other PaymentX read path (MCP's
    // payment.lookup, audit.search) already keys on - see event/ReconciliationEventConsumer's own
    // firstNonBlank(payload, "paymentReference", "reference") construction of InternalTransaction.
    // Most-recent-first because a reference can legitimately appear in more than one batch (e.g.
    // reprocessBatch re-running matching), and the newest comparison is the one evidence-based
    // analysis should read first.
    List<ReconciliationRecord> findByReferenceIdOrderByCreatedAtDesc(String referenceId);
}
