package com.paymentx.reconciliation.service.matching;

import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.dto.InternalTransaction;
import com.paymentx.reconciliation.entity.ReconciliationStatus;

import java.util.Optional;

/**
 * WHY a Strategy interface here, matching Notification Service's
 * NotificationChannelHandler pattern: the explicit "Support future
 * settlement providers" + "Use Strategy Pattern where appropriate"
 * requirements - different settlement providers may compare records
 * using different rules without changing MatchingEngine's orchestration
 * logic at all, exactly mirroring how NotificationChannelFactory lets
 * Push get added later with zero factory changes.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * TransactionMatcher is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.matching and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * TransactionMatcher PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.service.matching package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface TransactionMatcher {

    /** Compares one internal transaction against one external settlement
     *  record and classifies the outcome. Returns empty if this matcher
     *  cannot make a determination for this pair - MatchingEngine tries
     *  the next matcher in that case. */
    Optional<ReconciliationStatus> match(InternalTransaction internal, ExternalSettlementRecord external);
}
