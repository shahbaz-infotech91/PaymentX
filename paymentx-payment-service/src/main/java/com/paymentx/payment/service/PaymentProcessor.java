package com.paymentx.payment.service;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.entity.PaymentType;

/**
 * Strategy Pattern contract. Each concrete processor owns the execution
 * logic for exactly one PaymentType (DEBIT/CREDIT/RETURN/REVERSAL) plus
 * SettlementProcessor, which is not a PaymentType but a distinct pipeline
 * stage triggered after CREDIT succeeds - see SettlementProcessor's own
 * javadoc for why it still implements this same interface.
 *
 * WHY supports() lives ON the processor rather than PaymentEngineImpl
 * holding a hardcoded switch/if-else mapping PaymentType -> processor:
 * that mapping would need to change every time a new processor is added,
 * meaning PaymentEngineImpl (core orchestration logic) would need a code
 * change and redeploy for something as simple as adding a new payment
 * type. With supports() on each processor and PaymentEngineImpl building
 * its dispatch map from whatever processors Spring has instantiated,
 * adding a new PaymentType's processor is purely additive - a new
 * @Component class, zero changes to existing files. This is the Open/
 * Closed Principle applied concretely, not just cited as a buzzword.
 *
 * WHY the method returns ProcessingResult rather than void or throwing
 * on failure: a debit failing (e.g. insufficient funds at the rail) is
 * an EXPECTED business outcome, not an exceptional program state. Using
 * exceptions for expected business outcomes conflates "something broke"
 * with "the payment was declined," which makes it impossible for
 * PaymentEngineImpl to distinguish "retry this" from "this failed cleanly,
 * move to DEBIT_FAILED" without inspecting exception types/messages -
 * fragile. A typed result makes the distinction explicit at the interface
 * boundary.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentProcessor is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentProcessor PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentProcessor {

    boolean supports(PaymentType paymentType);

    ProcessingResult process(Payment payment);

    /**
     * outcome: whether this processor's step succeeded.
     * retryable: whether a failure is worth retrying (e.g. a downstream
     *            timeout) vs. terminal (e.g. insufficient funds - retrying
     *            the identical request will fail identically).
     * reason: human-readable detail, persisted to Payment.failureReason
     *         and PaymentAudit on failure.
     */
    record ProcessingResult(boolean success, boolean retryable, String reason) {
        public static ProcessingResult ok() {
            return new ProcessingResult(true, false, null);
        }

        public static ProcessingResult failure(String reason, boolean retryable) {
            return new ProcessingResult(false, retryable, reason);
        }
    }
}
