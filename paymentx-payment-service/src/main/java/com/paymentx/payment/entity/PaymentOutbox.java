package com.paymentx.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PaymentOutbox is a JPA entity in the payment module of PaymentX. It lives in package com.paymentx.payment.entity and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PaymentOutbox PaymentX ke payment module ka ek JPA entity hai. Ye com.paymentx.payment.entity package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class PaymentOutbox extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 64)
    private String topic;

    /**
     * Fully-serialized JSON of the event to publish (the common
     * PaymentEvent<T> envelope, serialized ahead of time). Storing the
     * pre-serialized string, rather than re-serializing at publish time,
     * guarantees the published payload is byte-identical to what was
     * decided inside the original transaction - no risk of a mapper
     * change between write-time and publish-time silently altering the
     * event's shape.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
