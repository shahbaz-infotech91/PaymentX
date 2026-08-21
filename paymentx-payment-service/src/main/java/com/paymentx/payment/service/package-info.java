/**
 * Business logic interfaces (contracts). Controllers and Kafka consumers depend on these interfaces, never on service.impl directly - this is what makes swapping an implementation (e.g. for testing) possible without touching callers.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * service is a package-level documentation summary in the payment module of PaymentX, package com.paymentx.payment.service. It is used within payment's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * service PaymentX ke payment module ka ek package-level documentation summary hai, package com.paymentx.payment.service me. Ye payment ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
package com.paymentx.payment.service;
