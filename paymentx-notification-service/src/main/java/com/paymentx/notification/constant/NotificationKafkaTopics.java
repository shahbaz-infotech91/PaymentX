package com.paymentx.notification.constant;

/**
 * WHY these exact topic literals are duplicated here rather than
 * importing each service's own KafkaTopics class: same established
 * platform convention as Audit Service's AuditKafkaTopics - each
 * service owns its own topic constants, no shared central registry.
 *
 * WHY PARTICIPANT_UPDATED, PAYMENT_CREATED and SECURITY_EVENT have no
 * topic listed: no currently-existing producer publishes any of them -
 * identical, already-documented gap to Audit Service's
 * TopicEventTypeResolver javadoc (Validation Service does not publish
 * participant CRUD to Kafka; Payment Service has no distinct "created"
 * event; API Gateway does not publish to Kafka at all). All three
 * SourceEventType enum values remain structurally supported for when a
 * producer eventually exists.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * NotificationKafkaTopics is a class in the notification module of PaymentX. It lives in package com.paymentx.notification.constant and participates in notification's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through notification's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * NotificationKafkaTopics PaymentX ke notification module ka ek class hai. Ye com.paymentx.notification.constant package me hai aur notification ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise notification ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class NotificationKafkaTopics {
    private NotificationKafkaTopics() {}

    public static final String INSTANT_PAYMENT_VALIDATED = "instant-payment-validated";
    public static final String CARD_PAYMENT_VALIDATED = "card-payment-validated";
    public static final String REAL_TIME_PAYMENT_VALIDATED = "real-time-payment-validated";

    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_DEBITED = "payment.debited";
    public static final String PAYMENT_CREDITED = "payment.credited";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_RETURNED = "payment.returned";
    public static final String PAYMENT_REVERSED = "payment.reversed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_TIMEOUT = "payment.timeout";

    public static final String ROUTING_ROUTE_RESOLVED = "routing.route-resolved";
    public static final String ROUTING_RULE_CHANGED = "routing.rule-changed";

    public static final String AUDIT_COMPLETED = "audit.event-recorded";

    public static final String[] ALL_CONSUMED_TOPICS = {
            INSTANT_PAYMENT_VALIDATED, CARD_PAYMENT_VALIDATED, REAL_TIME_PAYMENT_VALIDATED,
            PAYMENT_PROCESSING, PAYMENT_DEBITED, PAYMENT_CREDITED, PAYMENT_COMPLETED,
            PAYMENT_FAILED, PAYMENT_RETURNED, PAYMENT_REVERSED, PAYMENT_CANCELLED, PAYMENT_TIMEOUT,
            ROUTING_ROUTE_RESOLVED, ROUTING_RULE_CHANGED, AUDIT_COMPLETED
    };
}
