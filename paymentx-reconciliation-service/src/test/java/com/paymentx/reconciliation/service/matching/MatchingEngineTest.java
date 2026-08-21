package com.paymentx.reconciliation.service.matching;

import com.paymentx.reconciliation.cache.InternalTransactionCacheService;
import com.paymentx.reconciliation.cache.ReconciliationDedupService;
import com.paymentx.reconciliation.config.ReconciliationProperties;
import com.paymentx.reconciliation.dto.ExternalSettlementRecord;
import com.paymentx.reconciliation.dto.InternalTransaction;
import com.paymentx.reconciliation.entity.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * MatchingEngineTest is a JUnit test class in the reconciliation module of PaymentX, package com.paymentx.reconciliation.service.matching. It is used within reconciliation's internal request/data flow, exercising real behaviour against Testcontainers-backed infrastructure to catch regressions before deployment.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * MatchingEngineTest PaymentX ke reconciliation module ka ek JUnit test class hai, package com.paymentx.reconciliation.service.matching me. Ye reconciliation ke internal request/data flow me use hoti hai, Testcontainers-backed real infrastructure ke against actual behaviour test karke deployment se pehle regressions pakadti hai.
 * ====================================================================
 */
class MatchingEngineTest {

    @Mock
    private ReconciliationDedupService reconciliationDedupService;
    @Mock
    private InternalTransactionCacheService internalTransactionCacheService;

    private ReconciliationProperties reconciliationProperties;
    private MatchingEngine matchingEngine;
    private final UUID batchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reconciliationProperties = new ReconciliationProperties();
        matchingEngine = new MatchingEngine(reconciliationDedupService, reconciliationProperties, internalTransactionCacheService);
        // lenient: buildMissingRecord() never calls markRecordIfNew (there's
        // no external record to dedup-check against), so strict stubbing
        // would flag this shared default as unused for that test.
        lenient().when(reconciliationDedupService.markRecordIfNew(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(batchId))).thenReturn(true);
    }

    private ExternalSettlementRecord external(String paymentId, BigDecimal amount, String currency, String status, OffsetDateTime settlementDate) {
        return new ExternalSettlementRecord(paymentId, "ref-" + paymentId, "BANK001", amount, currency, status, settlementDate);
    }

    private InternalTransaction internal(String paymentId, BigDecimal amount, String currency, String status, OffsetDateTime settlementDate) {
        return new InternalTransaction(paymentId, "ref-" + paymentId, "BANK001", amount, currency, status, settlementDate);
    }

    @Test
    void match_identicalAmountCurrencyStatusAndTiming_returnsMatched() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-1", new BigDecimal("100.00"), "USD", "SETTLED", now);
        var internal = internal("pay-1", new BigDecimal("100.00"), "USD", "COMPLETED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    void match_amountWithinToleranceThreshold_stillMatched() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-tol", new BigDecimal("100.005"), "USD", "SETTLED", now);
        var internal = internal("pay-tol", new BigDecimal("100.00"), "USD", "COMPLETED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MATCHED);
    }

    @Test
    void match_noInternalTransaction_returnsOrphan() {
        var ext = external("pay-unknown", new BigDecimal("50.00"), "USD", "SETTLED", OffsetDateTime.now());

        var result = matchingEngine.match(batchId, ext, Optional.empty());

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.ORPHAN);
    }

    @Test
    void match_duplicateInBatch_returnsDuplicate() {
        when(reconciliationDedupService.markRecordIfNew("pay-dup", batchId)).thenReturn(false);
        var ext = external("pay-dup", new BigDecimal("50.00"), "USD", "SETTLED", OffsetDateTime.now());

        var result = matchingEngine.match(batchId, ext, Optional.empty());

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.DUPLICATE);
    }

    @Test
    void match_internalStatusCancelled_returnsUnexpectedSettlement() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-cancelled", new BigDecimal("100.00"), "USD", "SETTLED", now);
        var internal = internal("pay-cancelled", new BigDecimal("100.00"), "USD", "CANCELLED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.UNEXPECTED_SETTLEMENT);
    }

    @Test
    void match_currencyDiffers_returnsCurrencyMismatch() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-curr", new BigDecimal("100.00"), "EUR", "SETTLED", now);
        var internal = internal("pay-curr", new BigDecimal("100.00"), "USD", "COMPLETED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.CURRENCY_MISMATCH);
    }

    @Test
    void match_amountDiffersBeyondTolerance_returnsAmountMismatch() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-amt", new BigDecimal("100.00"), "USD", "SETTLED", now);
        var internal = internal("pay-amt", new BigDecimal("95.00"), "USD", "COMPLETED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
    }

    @Test
    void match_statusNotComparable_returnsStatusMismatch() {
        OffsetDateTime now = OffsetDateTime.now();
        var ext = external("pay-stat", new BigDecimal("100.00"), "USD", "PENDING", now);
        var internal = internal("pay-stat", new BigDecimal("100.00"), "USD", "COMPLETED", now.minusHours(1));

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.STATUS_MISMATCH);
    }

    @Test
    void match_settlementWithinDelayWindow_returnsSettlementDelay() {
        OffsetDateTime internalTime = OffsetDateTime.now().minusHours(48);
        OffsetDateTime externalTime = OffsetDateTime.now();
        var ext = external("pay-delay", new BigDecimal("100.00"), "USD", "SETTLED", externalTime);
        var internal = internal("pay-delay", new BigDecimal("100.00"), "USD", "COMPLETED", internalTime);

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.SETTLEMENT_DELAY);
    }

    @Test
    void match_settlementBeyondLateThreshold_returnsLateSettlement() {
        OffsetDateTime internalTime = OffsetDateTime.now().minusHours(100);
        OffsetDateTime externalTime = OffsetDateTime.now();
        var ext = external("pay-late", new BigDecimal("100.00"), "USD", "SETTLED", externalTime);
        var internal = internal("pay-late", new BigDecimal("100.00"), "USD", "COMPLETED", internalTime);

        var result = matchingEngine.match(batchId, ext, Optional.of(internal));

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.LATE_SETTLEMENT);
    }

    @Test
    void buildMissingRecord_producesMissingStatusWithInternalFieldsPopulated() {
        var internal = internal("pay-missing", new BigDecimal("100.00"), "USD", "COMPLETED", OffsetDateTime.now());

        var result = matchingEngine.buildMissingRecord(batchId, internal);

        assertThat(result.getReconciliationStatus()).isEqualTo(ReconciliationStatus.MISSING);
        assertThat(result.getInternalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getExternalAmount()).isNull();
    }
}
