package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.service.SchemeGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * See InstantPaymentGateway's javadoc for the full rationale. CARD_PAYMENT's RTP network
 * limit mirrors MAX_AMOUNT_CARD_PAYMENT already seeded in Validation Service.
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CardPaymentGateway is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CardPaymentGateway PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CardPaymentGateway implements SchemeGateway {

    private static final BigDecimal CARD_PAYMENT_PER_TRANSACTION_LIMIT = new BigDecimal("1000000.00");

    @Override
    public GatewayResult debit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("CARD_PAYMENT RTP debit accepted paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult credit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("CARD_PAYMENT RTP credit accepted paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult returnPayment(Payment payment) {
        log.info("CARD_PAYMENT RTP return accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult reverse(Payment payment) {
        log.info("CARD_PAYMENT RTP reversal accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    private GatewayResult validate(Payment payment) {
        if (payment.getAmount() == null || payment.getAmount().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return GatewayResult.failure("Amount must be positive", false);
        }
        if (payment.getAmount().getAmount().compareTo(CARD_PAYMENT_PER_TRANSACTION_LIMIT) > 0) {
            return GatewayResult.failure(
                    "Amount exceeds CARD_PAYMENT RTP per-transaction limit of " + CARD_PAYMENT_PER_TRANSACTION_LIMIT, false);
        }
        return null;
    }
}
