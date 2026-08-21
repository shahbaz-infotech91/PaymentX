package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.service.SchemeGateway;
import com.paymentx.payment.service.SchemeGatewayFactory;
import com.paymentx.payment.util.LoggingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles PaymentType.DEBIT - the debtor-side leg of a standard payment.
 * Delegates the actual rail interaction to the scheme-specific gateway
 * (via SchemeGatewayFactory) rather than a single generic gateway - see
 * SchemeGatewayFactory's javadoc.
 *
 * Deliberately stateless and side-effect-free beyond the gateway call -
 * this processor does NOT persist anything or transition Payment status
 * itself. PaymentEngineImpl owns all persistence and status-transition
 * responsibility, keeping processors focused purely on "attempt this one
 * operation, report the outcome."
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DebitProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DebitProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class DebitProcessor implements PaymentProcessor {

    private final SchemeGatewayFactory schemeGatewayFactory;

    @Override
    public boolean supports(PaymentType paymentType) {
        return paymentType == PaymentType.DEBIT;
    }

    @Override
    public ProcessingResult process(Payment payment) {
        LoggingContext.setPaymentId(payment.getId().toString());
        LoggingContext.setParticipantId(payment.getDebtorParticipantId());

        log.info("Executing debit paymentReference={} scheme={} amount={}",
                payment.getPaymentReference(), payment.getScheme(), payment.getAmount());

        SchemeGateway gateway = schemeGatewayFactory.getGateway(payment.getScheme());
        SchemeGateway.GatewayResult result = gateway.debit(payment);

        if (result.success()) {
            return ProcessingResult.ok();
        }
        log.warn("Debit failed paymentReference={} reason={} retryable={}",
                payment.getPaymentReference(), result.reason(), result.retryable());
        return ProcessingResult.failure(result.reason(), result.retryable());
    }
}
