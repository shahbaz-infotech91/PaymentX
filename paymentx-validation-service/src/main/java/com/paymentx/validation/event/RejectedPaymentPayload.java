package com.paymentx.validation.event;

import com.paymentx.validation.entity.Scheme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RejectedPaymentPayload is a class in the validation module of PaymentX. It lives in package com.paymentx.validation.event and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RejectedPaymentPayload PaymentX ke validation module ka ek class hai. Ye com.paymentx.validation.event package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RejectedPaymentPayload {
    private String paymentReference;
    private Scheme scheme;
    private String rejectionReason;
    private String errorCode;
}
