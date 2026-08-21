package com.paymentx.reconciliation.entity;

/**
 * The 10 possible outcomes of comparing one internal transaction against
 * settlement file records, per the explicit requirement list.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationStatus is a enum in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationStatus PaymentX ke reconciliation module ka ek enum hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum ReconciliationStatus {
    MATCHED,
    MISSING,
    DUPLICATE,
    AMOUNT_MISMATCH,
    CURRENCY_MISMATCH,
    STATUS_MISMATCH,
    SETTLEMENT_DELAY,
    LATE_SETTLEMENT,
    ORPHAN,
    UNEXPECTED_SETTLEMENT
}
