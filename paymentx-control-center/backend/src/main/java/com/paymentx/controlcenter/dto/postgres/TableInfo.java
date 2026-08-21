package com.paymentx.controlcenter.dto.postgres;

/**
 * ENGLISH: One real table's name and real row count within one of the
 * 7 PaymentX databases, read from information_schema.tables joined
 * with a real COUNT(*) - never an estimate. Used by the "table info"
 * endpoint the Phase 2 brief requires per database.
 *
 * HINGLISH: 7 PaymentX databases me se ek database ke andar ek real
 * table ka naam aur real row count, information_schema.tables se ek
 * real COUNT(*) ke saath padha gaya - kabhi estimate nahi. Phase 2
 * brief ke hisaab se har database ke liye "table info" endpoint isse
 * use karta hai.
 */
public record TableInfo(
        String tableName,
        long rowCount
) {
}
