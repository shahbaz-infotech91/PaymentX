package com.paymentx.reporting.entity;

/**
 * ====================================================================
 * ENGLISH:
 * Every report this service knows how to generate. Each value maps to
 * one ReportGenerator implementation (Strategy pattern - see
 * ReportGeneratorFactory) that knows how to pull data for that specific
 * report from the relevant source service's Kafka events / cache. This
 * enum exists so a report request can name WHICH report it wants
 * without the caller needing to know how that report is actually built.
 *
 * HINGLISH:
 * Ye enum har us report ko list karta hai jo ye service bana sakti hai.
 * Har value ek ReportGenerator implementation se juda hai (Strategy
 * pattern - ReportGeneratorFactory dekho) jo us specific report ka data
 * source service se laana jaanta hai. Iska matlab jab koi report
 * request karta hai, use bas naam batana hota hai ki kaunsa report
 * chahiye - ye jaanne ki zaroorat nahi ki wo report actually kaise
 * banta hai.
 * ====================================================================
 */
public enum ReportType {
    PAYMENT_SUMMARY,
    SETTLEMENT_SUMMARY,
    PARTICIPANT_SUMMARY,
    TRANSACTION_VOLUME,
    FAILED_TRANSACTIONS,
    SUCCESS_RATE,
    NOTIFICATION_SUMMARY,
    AUDIT_SUMMARY,
    ROUTING_SUMMARY,
    VALIDATION_SUMMARY,
    RECONCILIATION_SUMMARY
}
