/**
 * JPA entities mapping 1:1 to Liquibase-managed tables. Entities carry no business logic beyond simple invariants (e.g. a status-transition guard) - orchestration logic belongs in service.impl.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * entity is a package-level documentation summary in the payment module of PaymentX, package com.paymentx.payment.entity. It is used within payment's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * entity PaymentX ke payment module ka ek package-level documentation summary hai, package com.paymentx.payment.entity me. Ye payment ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
package com.paymentx.payment.entity;
