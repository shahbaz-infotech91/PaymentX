package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.entity.ReconciliationSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationSummaryRepository is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.repository and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationSummaryRepository PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.repository package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface ReconciliationSummaryRepository extends JpaRepository<ReconciliationSummary, UUID> {

    Optional<ReconciliationSummary> findByBatchId(UUID batchId);
}
