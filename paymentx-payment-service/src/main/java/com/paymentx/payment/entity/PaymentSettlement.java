package com.paymentx.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * externalSettlementRef -> settlementReference and settledAt ->
 * settlementDate: naming alignment only. settlementAmount/
 * settlementCurrency collapsed into an embedded Money, consistent with
 * Payment.amount - same DDD rationale (see Money.java).
 */
@Entity
@Table(name = "payment_settlement")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentSettlement is a JPA entity in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentSettlement PaymentX ke payment module ka ek JPA entity hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentSettlement extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    private SettlementStatus settlementStatus;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "settlement_amount", precision = 18, scale = 2))
    @AttributeOverride(name = "currency", column = @Column(name = "settlement_currency", length = 3))
    private Money settlementAmount;

    @Column(name = "settlement_reference", length = 128)
    private String settlementReference;

    @Column(name = "settlement_date")
    private OffsetDateTime settlementDate;
}
