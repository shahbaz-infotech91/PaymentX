package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The real connection + Liquibase status of one of the 7
 * PaymentX databases. What it does: reports whether a live JDBC
 * connection to that database actually succeeded (reachable), and if
 * so, real counts read from that database's own databasechangelog
 * table - the same table Liquibase itself writes to on every real
 * migration run - rather than re-invoking the Liquibase CLI. Why it
 * exists: "verify Liquibase migration status" per the Phase 2 brief,
 * done by reading Liquibase's own bookkeeping table instead of
 * simulating a migration run against a live database.
 *
 * HINGLISH: 7 PaymentX databases me se ek ka real connection +
 * Liquibase status. Ye kya karti hai: report karta hai ki us database
 * ka live JDBC connection actually successful hua (reachable) ya
 * nahi, aur agar hua toh us database ki apni databasechangelog table
 * se real counts padhta hai - wahi table jisme Liquibase khud har
 * real migration run par likhta hai - Liquibase CLI ko dobara invoke
 * karne ke bajaye. Ye dashboard me kyu hai: Phase 2 brief ke hisaab se
 * "verify Liquibase migration status", Liquibase ki apni bookkeeping
 * table padh kar kiya gaya, live database ke against ek migration run
 * simulate karne ke bajaye.
 */
public record DatabaseStatus(
        String databaseName,
        boolean reachable,
        String errorMessage,
        Long appliedChangesetCount,
        String lastChangesetId,
        OffsetDateTime lastChangesetExecutedAt,
        long responseTimeMillis
) {
}
