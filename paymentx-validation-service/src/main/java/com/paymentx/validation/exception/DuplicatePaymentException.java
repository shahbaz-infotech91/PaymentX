package com.paymentx.validation.exception;

import com.paymentx.common.exception.PaymentXException;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * DuplicatePaymentException is a exception in the validation module of PaymentX. It lives in package com.paymentx.validation.exception and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * DuplicatePaymentException PaymentX ke validation module ka ek exception hai. Ye com.paymentx.validation.exception package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class DuplicatePaymentException extends PaymentXException {
    public DuplicatePaymentException(String paymentReference) {
        super("DUPLICATE_PAYMENT_REFERENCE",
                "Payment with reference '" + paymentReference + "' has already been processed",
                false);
    }
}
