package com.paymentx.common.mapper;

import java.util.List;

/**
 * Generic entity/DTO mapping contract, meant to be EXTENDED by a
 * service's own {@code @Mapper}-annotated interface, e.g.
 * {@code interface PaymentMapper extends BaseMapper<Payment, PaymentDto> {}}
 * - MapStruct's annotation processor (already wired via this library's
 * own {@code pom.xml}, and available transitively to every consumer)
 * generates the implementation for whatever concrete {@code E}/{@code D}
 * the extending interface supplies.
 *
 * <p>Deliberately generic rather than bound to any concrete entity/DTO
 * pair - per ADR 0004, this library never introduces business-domain
 * models, so it cannot ship a real {@code Mapper} for one. What it CAN
 * ship is the repeated four-method shape (single + list, both
 * directions) every service's own mapper interface would otherwise
 * redeclare from scratch.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * BaseMapper is a interface in the common module of PaymentX. It lives in package com.paymentx.common.mapper and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * BaseMapper PaymentX ke common module ka ek interface hai. Ye com.paymentx.common.mapper package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public interface BaseMapper<E, D> {

    D toDto(E entity);

    E toEntity(D dto);

    List<D> toDtoList(List<E> entities);

    List<E> toEntityList(List<D> dtos);
}
