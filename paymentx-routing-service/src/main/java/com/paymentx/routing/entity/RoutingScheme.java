package com.paymentx.routing.entity;

/**
 * Service-local per the established platform convention (see ADR 0004
 * in paymentx-common-library docs) - business domain enums are NOT
 * shared via the common library, each service owns its own copy even
 * when the literal values line up with Validation/Payment Service's
 * PaymentScheme. This is a deliberate boundary, not an oversight: a
 * shared enum would couple this service's compile unit to every other
 * service's release cadence for a value set that is, in practice,
 * platform configuration data.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RoutingScheme is a enum in the routing module of PaymentX. It lives in package com.paymentx.routing.entity and participates in routing's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through routing's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RoutingScheme PaymentX ke routing module ka ek enum hai. Ye com.paymentx.routing.entity package me hai aur routing ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise routing ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public enum RoutingScheme {
    INSTANT_PAYMENT,
    REAL_TIME_PAYMENT,
    CARD_PAYMENT
}
