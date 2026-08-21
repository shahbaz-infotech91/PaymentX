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
 * Handles PaymentType.CREDIT dispatch AND is invoked directly by
 * PaymentEngineImpl as the second pipeline stage for a standard payment
 * after the debit leg succeeds. Uses the scheme-specific gateway via
 * SchemeGatewayFactory - see its javadoc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CreditProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CreditProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CreditProcessor implements PaymentProcessor {

    private final SchemeGatewayFactory schemeGatewayFactory;

    @Override
    public boolean supports(PaymentType paymentType) {
        return paymentType == PaymentType.CREDIT;
    }

    @Override
    public ProcessingResult process(Payment payment) {
        LoggingContext.setPaymentId(payment.getId().toString());
        LoggingContext.setParticipantId(payment.getCreditorParticipantId());

        log.info("Executing credit paymentReference={} scheme={} amount={}",
                payment.getPaymentReference(), payment.getScheme(), payment.getAmount());

        SchemeGateway gateway = schemeGatewayFactory.getGateway(payment.getScheme());
        SchemeGateway.GatewayResult result = gateway.credit(payment);

        if (result.success()) {
            return ProcessingResult.ok();
        }
        log.warn("Credit failed paymentReference={} reason={} retryable={}",
                payment.getPaymentReference(), result.reason(), result.retryable());
        return ProcessingResult.failure(result.reason(), result.retryable());
    }
}
