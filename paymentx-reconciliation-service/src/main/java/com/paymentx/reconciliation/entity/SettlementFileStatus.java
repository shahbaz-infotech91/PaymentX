package com.paymentx.reconciliation.entity;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFileStatus is a enum in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFileStatus PaymentX ke reconciliation module ka ek enum hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum SettlementFileStatus {
    UPLOADED,
    PROCESSING,
    PROCESSED,
    FAILED
}
