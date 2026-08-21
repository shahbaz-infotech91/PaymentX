package com.paymentx.reconciliation.event;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementCompletedEvent is a class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementCompletedEvent PaymentX ke reconciliation module ka ek class hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SettlementCompletedEvent {
    private UUID reconciliationRecordId;
    private String paymentId;
    private String participantId;
}
