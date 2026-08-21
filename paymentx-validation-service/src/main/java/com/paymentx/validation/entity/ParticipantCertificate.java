package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "certificate")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ParticipantCertificate is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ParticipantCertificate PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ParticipantCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "fingerprint", nullable = false, unique = true, length = 128)
    private String fingerprint;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    public boolean isExpired() {
        return expiresAt.isBefore(OffsetDateTime.now());
    }
}
