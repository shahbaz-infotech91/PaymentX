package com.paymentx.validation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "validation_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * ValidationLog is a JPA entity in the validation module of PaymentX. It lives in package com.paymentx.validation.entity and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * ValidationLog PaymentX ke validation module ka ek JPA entity hai. Ye com.paymentx.validation.entity package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class ValidationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false, length = 128)
    private String paymentReference;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheme", nullable = false, length = 16)
    private Scheme scheme;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "debtor_account", nullable = false, length = 64)
    private String debtorAccount;

    @Column(name = "debtor_bank_id", nullable = false, length = 32)
    private String debtorBankId;

    @Column(name = "creditor_account", nullable = false, length = 64)
    private String creditorAccount;

    @Column(name = "creditor_bank_id", nullable = false, length = 32)
    private String creditorBankId;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private ValidationStatus validationStatus;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    @Column(name = "validated_at", nullable = false, updatable = false)
    private OffsetDateTime validatedAt;
}
