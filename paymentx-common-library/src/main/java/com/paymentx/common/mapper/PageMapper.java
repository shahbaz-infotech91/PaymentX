package com.paymentx.common.mapper;

import com.paymentx.common.dto.PageResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Converts a Spring Data {@link Page} into the framework-agnostic
 * {@link PageResponse} an HTTP response should actually return - see
 * {@link PageResponse}'s javadoc for why the persistence-framework type
 * should never leak into a response contract directly. {@code Page}'s own
 * fields ({@code getNumber()}, {@code isFirst()}, {@code isLast()}, ...)
 * are read straight through rather than recomputed
 * ({@link PageResponse#of}'s arithmetic is for callers that only have raw
 * numbers, e.g. a non-JPA data source).
 *
 * <p>{@code jakarta.persistence}/{@code spring-data-jpa} are
 * {@code provided}-scope dependencies of this library (see
 * {@code pom.xml}) for exactly this reason: compile-time only, so a
 * service with no persistence layer at all never gets Postgres/Hibernate
 * forced onto its runtime classpath just for this mapper to compile.
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PageMapper is a class in the common module of PaymentX. It lives in package com.paymentx.common.mapper and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PageMapper PaymentX ke common module ka ek class hai. Ye com.paymentx.common.mapper package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public final class PageMapper {

    private PageMapper() {}

    public static <E, D> PageResponse<D> toPageResponse(Page<E> page, Function<E, D> converter) {

        List<D> content = page.getContent()
                .stream()
                .map(converter)
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        return toPageResponse(page, Function.identity());
    }}
