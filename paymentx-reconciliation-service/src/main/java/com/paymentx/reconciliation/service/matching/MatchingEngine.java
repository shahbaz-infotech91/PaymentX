package com.paymentx.reconciliation.service.matching;

import com.paymentx.reconciliation.cache.InternalTransactionCacheService;
import com.paymentx.reconciliation.cache.ReconciliationDedupService;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.dto.InternalTransaction;
import com.paymentx.reconciliation.entity.ReconciliationRecord;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * WHY this is a stateless, injectable @Component (not a static utility):
 * it depends on ReconciliationProperties (configurable thresholds - "No
 * hardcoded business logic" is explicit in the requirements) and
 * ReconciliationDedupService (duplicate detection needs shared state
 * across the whole batch).
 *
 * WHY classification precedence is DUPLICATE > terminal-status check
 * (UNEXPECTED_SETTLEMENT) > CURRENCY_MISMATCH > AMOUNT_MISMATCH >
 * STATUS_MISMATCH > settlement-timing (LATE/DELAY) > MATCHED: a
 * duplicate row is a data-quality problem independent of what it
 * contains, so it's checked first regardless of anything else. Currency
 * mismatch is checked before amount because comparing "100 USD" against
 * "100 EUR" numerically as "equal amounts" would be a materially wrong
 * classification.
 */
@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MatchingEngine is a component in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service.matching and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MatchingEngine PaymentX ke reconciliation module ka ek component hai. Ye com.paymentx.reconciliation.service.matching package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class MatchingEngine {

    private static final Set<String> TERMINAL_NON_SETTLING_STATUSES = Set.of("CANCELLED", "FAILED", "REVERSED", "TIMEOUT");
    private static final Set<String> SUCCESS_STATUS_SYNONYMS = Set.of("COMPLETED", "SETTLED", "SUCCESS", "SUCCESSFUL");

    private final ReconciliationDedupService reconciliationDedupService;
    private final ReconciliationProperties reconciliationProperties;
    private final InternalTransactionCacheService internalTransactionCacheService;

    /** Compares one external settlement row against its internal
     *  counterpart (if any) and builds the resulting ReconciliationRecord.
     *  Never returns null - MISSING/ORPHAN are both valid, real outcomes,
     *  not error conditions. */
    public ReconciliationRecord match(UUID batchId, ExternalSettlementRecord external, Optional<InternalTransaction> internalOpt) {
        if (!reconciliationDedupService.markRecordIfNew(external.paymentId(), batchId)) {
            return buildRecord(batchId, external, internalOpt, ReconciliationStatus.DUPLICATE);
        }

        if (internalOpt.isEmpty()) {
            return buildRecord(batchId, external, Optional.empty(), ReconciliationStatus.ORPHAN);
        }

        InternalTransaction internal = internalOpt.get();
        internalTransactionCacheService.clearPendingSettlement(internal.paymentId());

        if (TERMINAL_NON_SETTLING_STATUSES.contains(internal.status())) {
            return buildRecord(batchId, external, internalOpt, ReconciliationStatus.UNEXPECTED_SETTLEMENT);
        }

        if (!internal.currency().equalsIgnoreCase(external.currency())) {
            return buildRecord(batchId, external, internalOpt, ReconciliationStatus.CURRENCY_MISMATCH);
        }

        BigDecimal amountDifference = internal.amount().subtract(external.amount()).abs();
        if (amountDifference.compareTo(reconciliationProperties.getMatching().getAmountToleranceThreshold()) > 0) {
            return buildRecord(batchId, external, internalOpt, ReconciliationStatus.AMOUNT_MISMATCH);
        }

        if (!statusesComparable(internal.status(), external.status())) {
            return buildRecord(batchId, external, internalOpt, ReconciliationStatus.STATUS_MISMATCH);
        }

        ReconciliationStatus timingStatus = classifySettlementTiming(internal.settlementDate(), external.settlementDate());
        if (timingStatus != ReconciliationStatus.MATCHED) {
            return buildRecord(batchId, external, internalOpt, timingStatus);
        }

        return buildRecord(batchId, external, internalOpt, ReconciliationStatus.MATCHED);
    }

    /** Builds a MISSING record for an internal transaction that
     *  completed successfully but was never claimed by any external
     *  settlement row during this batch. */
    public ReconciliationRecord buildMissingRecord(UUID batchId, InternalTransaction internal) {
        return ReconciliationRecord.builder()
                .batchId(batchId)
                .paymentId(internal.paymentId())
                .referenceId(internal.referenceId())
                .participantId(internal.participantId())
                .internalAmount(internal.amount())
                .internalCurrency(internal.currency())
                .internalStatus(internal.status())
                .internalSettlementDate(internal.settlementDate())
                .reconciliationStatus(ReconciliationStatus.MISSING)
                .build();
    }

    private ReconciliationStatus classifySettlementTiming(OffsetDateTime internalDate, OffsetDateTime externalDate) {
        if (internalDate == null || externalDate == null) {
            return ReconciliationStatus.MATCHED;
        }
        long hoursLate = Duration.between(internalDate, externalDate).toHours();
        if (hoursLate <= 0) {
            return ReconciliationStatus.MATCHED;
        }
        if (hoursLate > reconciliationProperties.getMatching().getLateSettlementThresholdHours()) {
            return ReconciliationStatus.LATE_SETTLEMENT;
        }
        if (hoursLate > reconciliationProperties.getMatching().getSettlementDelayThresholdHours()) {
            return ReconciliationStatus.SETTLEMENT_DELAY;
        }
        return ReconciliationStatus.MATCHED;
    }

    /** WHY not a strict String.equals(): "COMPLETED" (Payment Service's
     *  internal terminology) and a settlement provider's own status
     *  vocabulary ("SETTLED", "SUCCESS") are semantically the same
     *  outcome expressed differently. */
    private boolean statusesComparable(String internalStatus, String externalStatus) {
        return SUCCESS_STATUS_SYNONYMS.contains(internalStatus.toUpperCase()) && SUCCESS_STATUS_SYNONYMS.contains(externalStatus.toUpperCase());
    }

    private ReconciliationRecord buildRecord(UUID batchId, ExternalSettlementRecord external,
                                              Optional<InternalTransaction> internalOpt, ReconciliationStatus status) {
        var builder = ReconciliationRecord.builder()
                .batchId(batchId)
                .paymentId(external.paymentId())
                .referenceId(external.referenceId())
                .participantId(external.participantId())
                .externalAmount(external.amount())
                .externalCurrency(external.currency())
                .externalStatus(external.status())
                .externalSettlementDate(external.settlementDate())
                .reconciliationStatus(status);

        internalOpt.ifPresent(internal -> builder
                .internalAmount(internal.amount())
                .internalCurrency(internal.currency())
                .internalStatus(internal.status())
                .internalSettlementDate(internal.settlementDate()));

        return builder.build();
    }
}
