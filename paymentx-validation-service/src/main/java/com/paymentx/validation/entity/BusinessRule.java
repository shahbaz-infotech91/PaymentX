package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "business_rule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BusinessRule is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BusinessRule PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class BusinessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 64)
    private String ruleCode;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 16)
    private Scheme scheme;

    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
