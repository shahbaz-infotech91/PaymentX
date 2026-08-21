package com.paymentx.reconciliation.repository;

import com.paymentx.reconciliation.entity.SettlementFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * SettlementFileRepository is a interface in the reconciliation module of PaymentX. It lives in package com.paymentx.reconciliation.repository and participates in reconciliation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through reconciliation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * SettlementFileRepository PaymentX ke reconciliation module ka ek interface hai. Ye com.paymentx.reconciliation.repository package me hai aur reconciliation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise reconciliation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface SettlementFileRepository extends JpaRepository<SettlementFile, UUID> {

    Optional<SettlementFile> findByChecksumHash(String checksumHash);

    boolean existsByChecksumHash(String checksumHash);
}
