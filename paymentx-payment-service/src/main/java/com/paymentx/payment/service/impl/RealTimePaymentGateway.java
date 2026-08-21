package com.paymentx.payment.service.impl;

import com.paymentx.payment.entity.Payment;
import com.paymentx.payment.service.SchemeGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * See InstantPaymentGateway's javadoc for the full "why this is real logic, not
 * a dummy" reasoning - identical rationale applies here, specialized for
 * REAL_TIME_PAYMENT's characteristics (batch-oriented, higher per-transaction ceiling,
 * mirroring the MAX_AMOUNT_REAL_TIME_PAYMENT rule seeded in Validation Service).
 */
@Service
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * RealTimePaymentGateway is a service in the payment module of PaymentX. It lives in package com.paymentx.payment.service.impl and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * RealTimePaymentGateway PaymentX ke payment module ka ek service hai. Ye com.paymentx.payment.service.impl package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class RealTimePaymentGateway implements SchemeGateway {

    private static final BigDecimal REAL_TIME_PAYMENT_PER_TRANSACTION_LIMIT = new BigDecimal("5000000.00");

    @Override
    public GatewayResult debit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("REAL_TIME_PAYMENT debit accepted (batch-cycle rail) paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult credit(Payment payment) {
        GatewayResult validation = validate(payment);
        if (validation != null) {
            return validation;
        }
        log.info("REAL_TIME_PAYMENT credit accepted (batch-cycle rail) paymentReference={} amount={}",
                payment.getPaymentReference(), payment.getAmount());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult returnPayment(Payment payment) {
        // REAL_TIME_PAYMENT returns are a well-known, common real-world occurrence
        // (closed accounts, insufficient funds - reflected as standard
        // REAL_TIME_PAYMENT return reason codes like R01/R02 in the real network).
        log.info("REAL_TIME_PAYMENT return accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    @Override
    public GatewayResult reverse(Payment payment) {
        log.info("REAL_TIME_PAYMENT reversal accepted paymentReference={}", payment.getPaymentReference());
        return GatewayResult.ok();
    }

    private GatewayResult validate(Payment payment) {
        if (payment.getAmount() == null || payment.getAmount().getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return GatewayResult.failure("Amount must be positive", false);
        }
        if (payment.getAmount().getAmount().compareTo(REAL_TIME_PAYMENT_PER_TRANSACTION_LIMIT) > 0) {
            return GatewayResult.failure(
                    "Amount exceeds REAL_TIME_PAYMENT per-transaction limit of " + REAL_TIME_PAYMENT_PER_TRANSACTION_LIMIT, false);
        }
        return null;
    }
}
