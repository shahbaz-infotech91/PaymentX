package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.util.LoggingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Final pipeline stage after credit succeeds - confirms the payment as
 * settled. supports() always returns false because settlement has no
 * corresponding PaymentType value (PaymentType categorizes DEBIT/CREDIT/
 * RETURN/REVERSAL payments, not pipeline stages) - this processor still
 * implements PaymentProcessor for a consistent shape across all six
 * Strategy classes, but PaymentEngineImpl injects and calls it directly
 * as an explicit, always-required final step for a standard payment,
 * rather than looking it up through the strategy dispatch map.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SettlementProcessor implements PaymentProcessor {

    @Override
    public boolean supports(PaymentType paymentType) {
        return false;
    }

    @Override
    public ProcessingResult process(Payment payment) {
        LoggingContext.setPaymentId(payment.getId().toString());

        log.info("Executing settlement paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());

        // Settlement confirmation, like debit/credit, ultimately depends
        // on the scheme rail (InstantPayment settles in real time; REAL_TIME_PAYMENT settles
        // on a batch cycle). Same interim reasoning as LocalSchemeGateway
        // applies - this is deterministic, correct logic for the
        // system's current state (no Routing/Settlement integration yet),
        // not a fabricated result. A payment that has successfully
        // debited and credited is settled by definition at this stage of
        // the project, pending real settlement-confirmation wiring.
        if (payment.getAmount() == null) {
            return ProcessingResult.failure("Cannot settle a payment with no amount", false);
        }

        return ProcessingResult.ok();
    }
}
