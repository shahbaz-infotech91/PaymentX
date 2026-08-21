package com.paymentx.reconciliation.constant;

/**
 * WHY PAYMENT_COMPLETED/FAILED/CANCELLED/RETURNED/REVERSED are the only
 * Payment Service topics consumed (not payment.processing/debited/
 * credited): only TERMINAL payment states are relevant to settlement
 * reconciliation - an in-flight payment.processing event has no
 * settlement outcome to reconcile against yet.
 *
 * WHY Notification Service has no topic listed: it does not currently
 * publish anything to Kafka - it is a terminal consumer with no
 * outbound producer. "Consume events from Notification Service" per
 * the requirements has no real topic to bind to today; this is the
 * same class of honest, documented gap already established elsewhere
 * in the platform (PARTICIPANT_UPDATED, PAYMENT_CREATED, SECURITY_EVENT).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReconciliationKafkaTopics is a class in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.constant and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReconciliationKafkaTopics PaymentX ke reconciliation module ka ek class hai. Ye com.paymentx.reconciliation.constant package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class ReconciliationKafkaTopics {
    private ReconciliationKafkaTopics() {}

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_RETURNED = "payment.returned";
    public static final String PAYMENT_REVERSED = "payment.reversed";

    public static final String AUDIT_COMPLETED = "audit.event-recorded";

    public static final String[] ALL_CONSUMED_TOPICS = {
            PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_CANCELLED, PAYMENT_RETURNED, PAYMENT_REVERSED, AUDIT_COMPLETED
    };

    public static final String RECONCILIATION_COMPLETED = "reconciliation.batch-completed";
    public static final String MISMATCH_DETECTED = "reconciliation.mismatch-detected";
    public static final String SETTLEMENT_COMPLETED = "reconciliation.settlement-completed";
}
