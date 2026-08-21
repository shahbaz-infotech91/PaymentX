package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "blacklist")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BlacklistEntry is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BlacklistEntry PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, length = 64)
    private String accountNumber;

    @Column(name = "bank_id", nullable = false, length = 32)
    private String bankId;

    @Column(name = "reason", nullable = false, length = 256)
    private String reason;

    @Column(name = "blacklisted_at", nullable = false, updatable = false)
    private OffsetDateTime blacklistedAt;

    @Column(name = "blacklisted_by", length = 64)
    private String blacklistedBy;
}
