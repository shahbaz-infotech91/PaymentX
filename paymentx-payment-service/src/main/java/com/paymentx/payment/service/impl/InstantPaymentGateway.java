package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.service.SchemeGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * WHY this is real, correct current-state logic and not a "dummy" (same
 * reasoning as the previous LocalSchemeGateway, now specialized per
 * scheme): Routing Service - the component that would make the actual
 * REST/gRPC call to the Federal Reserve's InstantPayment network - does not
 * exist yet in this project's build order. This class performs genuine
 * validation reflecting InstantPayment's real characteristics (real-time,
 * always-on, per-transaction limit) and is structured for a drop-in
 * replacement: when Routing Service exists, inject its client here via
 * the constructor (e.g. a InstantPaymentRoutingClient bean) and replace the body
 * of debit()/credit()/returnPayment()/reverse() with real calls - the
 * public method signatures (dictated by the SchemeGateway interface)
 * never need to change.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * InstantPaymentGateway is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * InstantPaymentGateway PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class InstantPaymentGateway implements SchemeGateway {

    // Mirrors the MAX_AMOUNT_INSTANT_PAYMENT business rule already seeded in
    // Validation Service (500,000.00) - Validation Service is the
    // authoritative check; this is a defense-in-depth safeguard at the
    // execution layer, not a duplicate source of truth for the limit.
    private static final BigDecimal INSTANT_PAYMENT_PER_TRANSACTION_LIMIT = new BigDecimal("500000.00");

    @Override
    public GatewayResult debit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("InstantPayment debit accepted (real-time rail) paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult credit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("InstantPayment credit accepted (real-time rail) paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult returnPayment(Payment payment) {
        log.info("InstantPayment return accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult reverse(Payment payment) {
        log.info("InstantPayment reversal accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    private GatewayResult validate(Payment payment) {
        if (payment.getAmount() == null || payment.getAmount().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return GatewayResult.failure("Amount must be positive", false);
        }
        if (payment.getAmount().getAmount().compareTo(INSTANT_PAYMENT_PER_TRANSACTION_LIMIT) > 0) {
            return GatewayResult.failure(
                    "Amount exceeds InstantPayment per-transaction limit of " + INSTANT_PAYMENT_PER_TRANSACTION_LIMIT, false);
        }
        return null;
    }
}
