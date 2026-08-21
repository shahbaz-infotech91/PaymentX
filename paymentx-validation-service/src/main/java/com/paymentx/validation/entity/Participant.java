package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "participant")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * Participant is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * Participant PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false, unique = true, length = 32)
    private String bankId;

    @Column(name = "legal_name", nullable = false, length = 256)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ParticipantStatus status;

    @Column(name = "onboarded_at", nullable = false, updatable = false)
    private OffsetDateTime onboardedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
