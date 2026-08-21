package com.paymentx.validation.repository;

import com.paymentx.validation.entity.ParticipantScheme;
import com.paymentx.validation.entity.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantSchemeRepository is a interface in the validation module of PaymentX. It lives in package com.paymentx.validation.repository and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantSchemeRepository PaymentX ke validation module ka ek interface hai. Ye com.paymentx.validation.repository package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface ParticipantSchemeRepository extends JpaRepository<ParticipantScheme, Long> {
    Optional<ParticipantScheme> findByParticipantIdAndSchemeAndActiveTrue(Long participantId, Scheme scheme);
}
