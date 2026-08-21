package com.paymentx.reconciliation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementCompletionApplicationEvent is a class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.event and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementCompletionApplicationEvent PaymentX ke reconciliation module ka ek class hai. Ye com.paymentx.reconciliation.event package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SettlementCompletionApplicationEvent extends ApplicationEvent {
    private final SettlementCompletedEvent payload;
    private final String traceId;

    public SettlementCompletionApplicationEvent(Object source, SettlementCompletedEvent payload, String traceId) {
        super(source);
        this.payload = payload;
        this.traceId = traceId;
    }
}
