package com.paymentx.payment.service;

import com.paymentx.payment.entity.Payment;

/**
 * The abstraction boundary between Payment Service's business logic and
 * the actual payment rail (InstantPayment/CARD_PAYMENT/REAL_TIME_PAYMENT), which in the target
 * architecture is reached via Routing Service - a module not yet built.
 * DebitProcessor/CreditProcessor depend on THIS interface, never on a
 * concrete rail-calling implementation directly, so that when Routing
 * Service exists, swapping LocalSchemeGateway for a real
 * RoutingServiceGateway is a single-class change with zero impact on
 * processor logic, transaction boundaries, or the outbox pattern already
 * wired around these calls.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SchemeGateway is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SchemeGateway PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface SchemeGateway {

    GatewayResult debit(Payment payment);

    GatewayResult credit(Payment payment);

    // Named returnPayment(), not return() - `return` is a reserved Java keyword.
    GatewayResult returnPayment(Payment payment);

    GatewayResult reverse(Payment payment);

    record GatewayResult(boolean success, boolean retryable, String reason) {
        public static GatewayResult ok() {
            return new GatewayResult(true, false, null);
        }

        public static GatewayResult failure(String reason, boolean retryable) {
            return new GatewayResult(false, retryable, reason);
        }
    }
}
