package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "participant_scheme")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantScheme is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantScheme PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 16)
    private Scheme scheme;

    @Column(name = "active", nullable = false)
    private boolean active;
}
