package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.entity.MismatchRecord;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MismatchRecordSpecifications is a class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.repository and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MismatchRecordSpecifications PaymentX ke reconciliation module ka ek class hai. Ye com.paymentx.reconciliation.repository package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class MismatchRecordSpecifications {

    private MismatchRecordSpecifications() {}

    public static Specification<MismatchRecord> hasBatchId(UUID batchId) {
        return (root, query, cb) -> batchId == null ? cb.conjunction() : cb.equal(root.get("batchId"), batchId);
    }

    public static Specification<MismatchRecord> hasMismatchType(ReconciliationStatus mismatchType) {
        return (root, query, cb) -> mismatchType == null ? cb.conjunction() : cb.equal(root.get("mismatchType"), mismatchType);
    }

    public static Specification<MismatchRecord> isResolved(Boolean resolved) {
        return (root, query, cb) -> resolved == null ? cb.conjunction() : cb.equal(root.get("resolved"), resolved);
    }
}
