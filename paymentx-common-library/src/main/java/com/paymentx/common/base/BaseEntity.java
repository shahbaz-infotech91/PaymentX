package com.paymentx.common.base;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Root of the shared entity hierarchy. Provides only identity - a UUID
 * primary key using native Hibernate 6 / JPA 3.1 generation (see
 * Payment Service's {@code Payment} entity for the same pattern, already
 * proven in production use in this project).
 *
 * <p>WHY this is NOT retrofitted onto Payment Service's existing
 * {@code Auditable}/{@code ImmutableAuditable} hierarchy: those classes
 * are already working, tested, and depended upon by six live entities.
 * Migrating them onto this shared base is a real refactor with real risk
 * and belongs to a dedicated, deliberate follow-up task - not bundled
 * silently into a library-introduction migration. This base class is
 * for services that don't have a hierarchy yet (Routing, Audit,
 * Notification, Reconciliation, Reporting).
 */
@MappedSuperclass
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BaseEntity is a class in the common module of PaymentX. It lives in package com.paymentx.common.base and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BaseEntity PaymentX ke common module ka ek class hai. Ye com.paymentx.common.base package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
}
