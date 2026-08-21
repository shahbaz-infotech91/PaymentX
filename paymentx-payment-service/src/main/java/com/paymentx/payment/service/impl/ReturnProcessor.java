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
 * Handles PaymentType.RETURN - a creditor-bank-initiated "give the money
 * back" (see PaymentReturnedEvent's javadoc for the return-vs-reversal
 * distinction). Selected via the strategy map when a return flow is
 * triggered - the trigger point (a REST endpoint or an inbound return
 * event from a bank) is outside this batch's scope; this class is
 * complete and correct, ready to be wired to that entry point later.
 */
@Component
@RequiredArgsConstructor
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ReturnProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ReturnProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ReturnProcessor implements PaymentProcessor {

    private final SchemeGatewayFactory schemeGatewayFactory;

    @Override
    public boolean supports(PaymentType paymentType) {
        return paymentType == PaymentType.RETURN;
    }

    @Override
    public ProcessingResult process(Payment payment) {
        LoggingContext.setPaymentId(payment.getId().toString());

        log.info("Executing return paymentReference={} scheme={} amount={}",
                payment.getPaymentReference(), payment.getScheme(), payment.getAmount());

        if (payment.getStatus() == null) {
            return ProcessingResult.failure("Payment has no status - cannot process return", false);
        }

        SchemeGateway gateway = schemeGatewayFactory.getGateway(payment.getScheme());
        SchemeGateway.GatewayResult result = gateway.returnPayment(payment);

        if (result.success()) {
            return ProcessingResult.ok();
        }
        log.warn("Return failed paymentReference={} reason={} retryable={}",
                payment.getPaymentReference(), result.reason(), result.retryable());
        return ProcessingResult.failure(result.reason(), result.retryable());
    }
}
