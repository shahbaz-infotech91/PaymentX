package com.paymentx.payment.repository;

import com.paymentx.payment.entity.PaymentSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentSettlementRepository is a interface in the payment module of PaymentX. It lives in package com.paymentx.payment.repository and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentSettlementRepository PaymentX ke payment module ka ek interface hai. Ye com.paymentx.payment.repository package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlement, UUID> {

    Optional<PaymentSettlement> findByPaymentId(UUID paymentId);
}
