package com.paymentx.audit.constant;

/**
 * WHY these exact topic literals are duplicated here rather than
 * importing Payment/Validation/Routing Service's own KafkaTopics
 * classes: each service owns its own topic constants per the platform's
 * established convention (no shared central topic registry - see
 * ADR 0002/every other service's equivalent class) - a consumer
 * referencing another module's Java constant would require a compile
 * dependency on that service's JAR, which does not exist and should not
 * be introduced.
 *
 * WHY no "API Gateway" topics are listed: Gateway does not currently
 * publish to Kafka at all (it is a pure HTTP proxy - see its
 * GatewayConfig/filter package). Consuming "API Request"/"API Response"
 * events from Gateway would require adding a Kafka producer there first,
 * which is out of this task's scope ("do not modify any other service
 * unless absolutely required" - a new Gateway producer is a materially
 * bigger change than "absolutely required" justifies). Audit Service's
 * own AuditController.recordApiEvent() endpoint (see AuditController)
 * exists as the documented alternative path for API-request/response and
 * security events until Gateway grows a producer.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * AuditKafkaTopics is a class in the audit module of PaymentX. It lives in package com.paymentx.audit.constant and participates in audit's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through audit's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * AuditKafkaTopics PaymentX ke audit module ka ek class hai. Ye com.paymentx.audit.constant package me hai aur audit ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise audit ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class AuditKafkaTopics {
    private AuditKafkaTopics() {}

    // Validation Service outputs
    public static final String INSTANT_PAYMENT_VALIDATED = "instant-payment-validated";
    public static final String CARD_PAYMENT_VALIDATED = "card-payment-validated";
    public static final String REAL_TIME_PAYMENT_VALIDATED = "real-time-payment-validated";

    // Payment Service outputs
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_DEBITED = "payment.debited";
    public static final String PAYMENT_CREDITED = "payment.credited";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_RETURNED = "payment.returned";
    public static final String PAYMENT_REVERSED = "payment.reversed";
    public static final String PAYMENT_CANCELLED = "payment.cancelled";
    public static final String PAYMENT_TIMEOUT = "payment.timeout";

    // Routing Service outputs
    public static final String ROUTING_ROUTE_RESOLVED = "routing.route-resolved";
    public static final String ROUTING_RULE_CHANGED = "routing.rule-changed";

    public static final String[] ALL_CONSUMED_TOPICS = {
            INSTANT_PAYMENT_VALIDATED, CARD_PAYMENT_VALIDATED, REAL_TIME_PAYMENT_VALIDATED,
            PAYMENT_PROCESSING, PAYMENT_DEBITED, PAYMENT_CREDITED, PAYMENT_COMPLETED,
            PAYMENT_FAILED, PAYMENT_RETURNED, PAYMENT_REVERSED, PAYMENT_CANCELLED, PAYMENT_TIMEOUT,
            ROUTING_ROUTE_RESOLVED, ROUTING_RULE_CHANGED
    };

    // Published by this service after successfully recording an audit
    // event sourced from Kafka - lets any downstream consumer (e.g. a
    // future compliance/reporting service) know an event has been
    // durably audited, without needing to poll this service's REST API.
    public static final String AUDIT_COMPLETED = "audit.event-recorded";
}
