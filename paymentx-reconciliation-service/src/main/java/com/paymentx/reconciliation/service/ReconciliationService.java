package com.paymentx.reconciliation.service;

import com.paymentx.common.dto.PageResponse;
import com.paymentx.reconciliation.dto.BatchResponse;
import com.paymentx.reconciliation.dto.MismatchRecordResponse;
import com.paymentx.reconciliation.dto.MismatchSearchCriteria;
import com.paymentx.reconciliation.dto.ReconciliationSummaryResponse;
import com.paymentx.reconciliation.dto.SettlementFileResponse;
import com.paymentx.reconciliation.dto.StartReconciliationRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationService is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.service and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationService PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.service package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface ReconciliationService {

    SettlementFileResponse uploadSettlementFile(MultipartFile file);

    /** Synchronous kickoff, async execution - returns immediately with
     *  status PENDING/RUNNING while the actual matching runs on a
     *  dedicated executor. */
    BatchResponse startReconciliation(StartReconciliationRequest request, String triggeredBy);

    BatchResponse getBatchStatus(UUID batchId);

    ReconciliationSummaryResponse getSummary(UUID batchId);

    PageResponse<MismatchRecordResponse> searchMismatches(MismatchSearchCriteria criteria, int page, int size);

    MismatchRecordResponse resolveMismatch(UUID mismatchId, String resolvedBy, String notes);

    /** Re-runs matching for records already in a batch without needing
     *  to re-upload the settlement file - admin-only. */
    BatchResponse reprocessBatch(UUID batchId, String triggeredBy);

    /** CSV report of every ReconciliationRecord in a batch, for download. */
    byte[] downloadReport(UUID batchId);
}
