package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The Transaction Monitor's real filter/search/sort request -
 * every field here is optional and, when present, becomes exactly one
 * parameterized WHERE fragment in PaymentRepository.findFiltered()
 * (never a client-supplied SQL string). search matches payment_
 * reference/correlation_id/trace_id (ILIKE, partial match); status/
 * scheme are exact matches against the real payment.status/scheme
 * columns; participantId matches either debtor or creditor; dateFrom/
 * dateTo bound created_at.
 *
 * HINGLISH: Transaction Monitor ka real filter/search/sort request -
 * yahan har field optional hai aur, jab present ho, PaymentRepository.
 * findFiltered() me exactly ek parameterized WHERE fragment ban jaata
 * hai (kabhi client-supplied SQL string nahi). search payment_
 * reference/correlation_id/trace_id se match karta hai (ILIKE,
 * partial match); status/scheme real payment.status/scheme columns ke
 * against exact matches hain; participantId debtor ya creditor dono
 * se match karta hai; dateFrom/dateTo created_at ko bound karte hain.
 */
public record PaymentFilter(
        String search,
        String status,
        String scheme,
        String participantId,
        OffsetDateTime dateFrom,
        OffsetDateTime dateTo,
        PaymentSortField sortField,
        boolean sortAscending
) {
}
