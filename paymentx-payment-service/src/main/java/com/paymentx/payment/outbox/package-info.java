/**
 * The Outbox Pattern implementation: the outbox entity/repository interaction and the logic that atomically writes a pending-event row in the same transaction as the payment state change.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * outbox is a package-level documentation summary in the payment module of PaymentX, package com.paymentx.payment.outbox. It is used within payment's internal request/data flow, and where applicable is reached indirectly by other PaymentX services through this module's REST API or Kafka events.
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * outbox PaymentX ke payment module ka ek package-level documentation summary hai, package com.paymentx.payment.outbox me. Ye payment ke internal request/data flow me use hoti hai, aur jahan applicable ho, dusri PaymentX services ise is module ke REST API ya Kafka events ke through indirectly use karti hain.
 * ====================================================================
 */
package com.paymentx.payment.outbox;
