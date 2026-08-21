/**
 * Spring @Scheduled jobs: the outbox-draining poller (publishes pending outbox rows to Kafka) and the retry-scan job (finds RETRYING payments whose backoff window has elapsed).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * scheduler is a package-level documentation summary in the payment module of PaymentX, package com.paymentx.payment.scheduler. It is used within payment's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * scheduler PaymentX ke payment module ka ek package-level documentation summary hai, package com.paymentx.payment.scheduler me. Ye payment ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
package com.paymentx.payment.scheduler;
