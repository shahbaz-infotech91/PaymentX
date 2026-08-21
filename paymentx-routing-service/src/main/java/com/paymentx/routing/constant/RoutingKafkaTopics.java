package com.paymentx.routing.constant;

/**
 * Service-local per the established convention (each service owns its
 * OWN topic constants, no shared central topic registry - see
 * Validation/Payment Service's equivalent classes).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingKafkaTopics is a class in the routing module of PaymentX. It lives in package com.paymentx.routing.constant and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingKafkaTopics PaymentX ke routing module ka ek class hai. Ye com.paymentx.routing.constant package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class RoutingKafkaTopics {
    private RoutingKafkaTopics() {}

    public static final String ROUTE_RESOLVED = "routing.route-resolved";
    public static final String RULE_CHANGED = "routing.rule-changed";
}
