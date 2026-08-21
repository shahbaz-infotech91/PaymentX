package com.paymentx.common.dto;

import java.util.List;

/**
 * WHY this exists instead of every service returning Spring Data's
 * {@code Page<T>} directly from REST controllers: {@code Page} serializes
 * with Spring Data-internal field names and includes a {@code Pageable}
 * object with more detail than an API consumer needs - it also silently
 * changes shape across Spring Data versions. A stable, hand-defined DTO
 * decouples the wire contract from the persistence framework version -
 * same principle as every service's own DTOs decoupling from JPA
 * entities (see Payment Service's {@code PaymentResponse} javadoc for
 * the original statement of this principle).
 */
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * PageResponse is a record (DTO) in the common module of PaymentX. It lives in package com.paymentx.common.dto and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * PageResponse PaymentX ke common module ka ek record (DTO) hai. Ye com.paymentx.common.dto package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        return new PageResponse<>(
                content,
                pageNumber,
                pageSize,
                totalElements,
                totalPages,
                pageNumber == 0,
                pageNumber >= totalPages - 1
        );
    }
}
