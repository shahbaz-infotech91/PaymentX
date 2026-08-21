package com.paymentx.payment.constant;

/**
 * WHY input and output topic names use different conventions
 * (hyphen-separated vs dot-separated): this is an inherited inconsistency,
 * not a new decision. The INPUT topics (INSTANT_PAYMENT_VALIDATED etc.) already
 * exist as published by Validation Service using hyphen-separated names.
 * The OUTPUT topics use dot-separated names per this service's own
 * specification. Flagging this explicitly rather than silently
 * "fixing" it by renaming Validation Service's already-deployed topics
 * (a breaking change for any other consumer) or silently diverging from
 * the given output naming. This inconsistency should be resolved via a
 * project-wide naming ADR before Routing Service (which will both consume
 * AND produce topics) is built - see docs/adr/.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * KafkaTopics is a class in the payment module of PaymentX. It lives in package com.paymentx.payment.constant and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * KafkaTopics PaymentX ke payment module ka ek class hai. Ye com.paymentx.payment.constant package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class KafkaTopics {
    private KafkaTopics() {}

    // Input - consumed from Validation Service
    public static final String INSTANT_PAYMENT_VALIDATED = "instant-payment-validated";
    public static final String CARD_PAYMENT_VALIDATED = "card-payment-validated";
    public static final String REAL_TIME_PAYMENT_VALIDATED = "real-time-payment-validated";

    // Output - published by this service across the payment lifecycle
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_DEBITED = "payment.debited";
    public static final String PAYMENT_CREDITED = "payment.credited";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_RETURNED = "payment.returned";
    public static final String PAYMENT_REVERSED = "payment.reversed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_TIMEOUT = "payment.timeout";
}
