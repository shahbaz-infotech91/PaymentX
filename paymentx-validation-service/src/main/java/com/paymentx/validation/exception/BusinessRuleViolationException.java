package com.paymentx.validation.exception;

import com.paymentx.common.exception.PaymentXException;

/**
 * Thrown when a payment fails a BUSINESS rule (blacklist, amount limit) -
 * as opposed to a schema/structural failure, which is caught earlier by
 * Jakarta Bean Validation and never reaches the service layer at all.
 *
 * Extends the shared PaymentXException from `common` so every service's
 * exceptions carry the same errorCode/retryable contract - this is the
 * "contracts in common" principle from Module 1 in action.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BusinessRuleViolationException is a exception in the validation module of PaymentX. It lives in package com.paymentx.validation.exception and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BusinessRuleViolationException PaymentX ke validation module ka ek exception hai. Ye com.paymentx.validation.exception package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BusinessRuleViolationException extends PaymentXException {

    public BusinessRuleViolationException(String errorCode, String message) {
        // Business rule violations (blacklisted account, amount over limit)
        // are NEVER retryable - retrying the exact same payment will fail
        // the exact same rule again. Contrast this with a network timeout,
        // which WOULD be retryable. This distinction is what lets Routing
        // Service later decide "should I automatically retry this failure
        // or surface it to a human" without re-deriving that logic itself.
        super(errorCode, message, false);
    }
}
