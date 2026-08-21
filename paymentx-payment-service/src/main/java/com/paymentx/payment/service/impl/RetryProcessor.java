package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentType;
import com.paymentx.payment.service.PaymentProcessor;
import com.paymentx.payment.util.LoggingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WHY RetryProcessor is unlike the other five: it does not own a single
 * PaymentType - its job is "re-run whichever processor originally
 * handled this payment's type." supports() therefore matches every
 * type the four delegates collectively support.
 *
 * WHY this constructor takes four EXPLICIT concrete processor types
 * rather than `List<PaymentProcessor> allProcessors` (which would seem
 * more consistent with PaymentEngineImpl's approach): RetryProcessor is
 * itself a PaymentProcessor bean. Injecting the full List<PaymentProcessor>
 * here would require Spring to construct every PaymentProcessor bean -
 * including RetryProcessor itself - before RetryProcessor's own
 * constructor can run, a genuine circular dependency
 * (BeanCurrentlyInCreationException at startup). Depending on the four
 * concrete classes directly breaks that cycle, since none of them depend
 * back on RetryProcessor.
 */
@Component
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RetryProcessor is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RetryProcessor PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RetryProcessor implements PaymentProcessor {

    private final Map<PaymentType, PaymentProcessor> delegates;

    public RetryProcessor(DebitProcessor debitProcessor,
                           CreditProcessor creditProcessor,
                           ReturnProcessor returnProcessor,
                           ReversalProcessor reversalProcessor) {
        this.delegates = Map.of(
                PaymentType.DEBIT, debitProcessor,
                PaymentType.CREDIT, creditProcessor,
                PaymentType.RETURN, returnProcessor,
                PaymentType.REVERSAL, reversalProcessor
        );
    }

    @Override
    public boolean supports(PaymentType paymentType) {
        return delegates.containsKey(paymentType);
    }

    @Override
    public ProcessingResult process(Payment payment) {
        LoggingContext.setPaymentId(payment.getId().toString());

        PaymentProcessor delegate = delegates.get(payment.getPaymentType());
        if (delegate == null) {
            return ProcessingResult.failure(
                    "No retry-capable processor registered for type " + payment.getPaymentType(), false);
        }

        log.info("Retrying via delegate={} paymentReference={}",
                delegate.getClass().getSimpleName(), payment.getPaymentReference());

        return delegate.process(payment);
    }
}
