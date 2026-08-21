package com.paymentx.payment.service;

import com.paymentx.payment.dto.CancellationRequest;
import com.paymentx.payment.dto.PaymentResponse;
import com.paymentx.payment.dto.PaymentRetryRequest;

/**
 * Deliberately limited to retry and cancel - per the explicit
 * instruction, debit/credit/return/reversal commands do NOT get REST
 * entry points here; they continue to enter exclusively via
 * Gateway -> Validation Service -> Kafka -> PaymentEngine. Both methods
 * here work by writing to existing tables that already-running
 * background components (RetryScheduler, OutboxProcessor) consume - they
 * never call PaymentEngine or any PaymentProcessor directly.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentOperationService is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.service and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentOperationService PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.service package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentOperationService {

    PaymentResponse retry(String paymentReference, PaymentRetryRequest request);

    PaymentResponse cancel(String paymentReference, CancellationRequest request);
}
