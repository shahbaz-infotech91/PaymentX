package com.paymentx.reconciliation.mapper;

import com.paymentx.reconciliation.dto.BatchResponse;
import com.paymentx.reconciliation.dto.MismatchRecordResponse;
import com.paymentx.reconciliation.dto.ReconciliationRecordResponse;
import com.paymentx.reconciliation.dto.ReconciliationSummaryResponse;
import com.paymentx.reconciliation.dto.SettlementFileResponse;
import com.paymentx.reconciliation.entity.MismatchRecord;
import com.paymentx.reconciliation.entity.ReconciliationBatch;
import com.paymentx.reconciliation.entity.ReconciliationRecord;
import com.paymentx.reconciliation.entity.ReconciliationSummary;
import com.paymentx.reconciliation.entity.SettlementFile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationMapper is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.mapper and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationMapper PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.mapper package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface ReconciliationMapper {

    BatchResponse toResponse(ReconciliationBatch entity);

    MismatchRecordResponse toResponse(MismatchRecord entity);

    ReconciliationRecordResponse toResponse(ReconciliationRecord entity);

    SettlementFileResponse toResponse(SettlementFile entity);

    ReconciliationSummaryResponse toResponse(ReconciliationSummary entity);
}
