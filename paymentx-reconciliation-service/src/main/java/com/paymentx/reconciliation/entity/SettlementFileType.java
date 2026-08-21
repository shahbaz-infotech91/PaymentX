package com.paymentx.reconciliation.entity;

/**
 * XML is a structurally-supported enum value with no
 * SettlementFileImporter implementation yet - see
 * SettlementFileImporterFactory's javadoc for the "future-ready XML"
 * extension-point rationale, mirroring Notification Service's PUSH
 * channel pattern.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFileType is a enum in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.entity and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFileType PaymentX ke reconciliation module ka ek enum hai. Ye com.paymentx.reconciliation.entity package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum SettlementFileType {
    CSV,
    JSON,
    XML
}
