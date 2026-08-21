package com.paymentx.reporting.constant;

/**
 * ====================================================================
 * ENGLISH: All topics Reporting Service consumes (populating
 * SourceEvent for aggregation) and publishes (ReportGenerated/Failed/
 * Completed). "Consume events if needed" is explicit softer language
 * than other services' mandatory consumption - this list covers every
 * real, currently-existing topic across Payment/Validation/Audit/
 * Routing/Reconciliation Service.
 *
 * HINGLISH: Sab topics jo Reporting Service consume karta hai
 * (SourceEvent ko aggregation ke liye populate karne ke liye) aur
 * publish karta hai. Ye list Payment/Validation/Audit/Routing/
 * Reconciliation Service ke har real, currently-existing topic ko
 * cover karti hai.
 * ====================================================================
 */
public final class ReportingKafkaTopics {
    private ReportingKafkaTopics() {}

    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_RETURNED = "payment.returned";
    public static final String PAYMENT_REVERSED = "payment.reversed";

    public static final String INSTANT_PAYMENT_VALIDATED = "instant-payment-validated";
    public static final String CARD_PAYMENT_VALIDATED = "card-payment-validated";
    public static final String REAL_TIME_PAYMENT_VALIDATED = "real-time-payment-validated";

    public static final String AUDIT_COMPLETED = "audit.event-recorded";
    public static final String ROUTING_RULE_CHANGED = "routing.rule-changed";
    public static final String RECONCILIATION_BATCH_COMPLETED = "reconciliation.batch-completed";

    public static final String[] ALL_CONSUMED_TOPICS = {
            PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_CANCELLED, PAYMENT_RETURNED, PAYMENT_REVERSED,
            INSTANT_PAYMENT_VALIDATED, CARD_PAYMENT_VALIDATED, REAL_TIME_PAYMENT_VALIDATED,
            AUDIT_COMPLETED, ROUTING_RULE_CHANGED, RECONCILIATION_BATCH_COMPLETED
    };

    public static final String REPORT_GENERATED = "reporting.report-generated";
    public static final String REPORT_FAILED = "reporting.report-failed";
    public static final String REPORT_COMPLETED = "reporting.report-completed";
}
