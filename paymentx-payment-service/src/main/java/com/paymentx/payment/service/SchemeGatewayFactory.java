package com.paymentx.payment.service;

import com.paymentx.payment.entity.PaymentScheme;
import com.paymentx.payment.service.impl.RealTimePaymentGateway;
import com.paymentx.payment.service.impl.InstantPaymentGateway;
import com.paymentx.payment.service.impl.CardPaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * WHY a fixed three-way switch rather than the "inject List<SchemeGateway>,
 * filter by a supports() method" pattern used for PaymentProcessor: there
 * are exactly three schemes (INSTANT_PAYMENT, REAL_TIME_PAYMENT, CARD_PAYMENT), a closed, stable set
 * defined by the payment networks PaymentX integrates with - unlike
 * PaymentType, which is plausibly extended by future business
 * requirements. A switch over PaymentScheme, explicitly wired to three
 * named beans, is simpler and equally correct for a genuinely closed
 * set; the Java compiler also enforces exhaustiveness here (a new enum
 * constant without a corresponding case fails to compile), which a
 * runtime supports()-scan cannot guarantee.
 */
@Component
@RequiredArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SchemeGatewayFactory is a component in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SchemeGatewayFactory PaymentX ke payment module ka ek component hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class SchemeGatewayFactory {

    private final InstantPaymentGateway instantPaymentGateway;
    private final RealTimePaymentGateway realTimePaymentGateway;
    private final CardPaymentGateway cardPaymentGateway;

    public SchemeGateway getGateway(PaymentScheme scheme) {
        return switch (scheme) {
            case INSTANT_PAYMENT -> instantPaymentGateway;
            case REAL_TIME_PAYMENT -> realTimePaymentGateway;
            case CARD_PAYMENT -> cardPaymentGateway;
        };
    }
}
