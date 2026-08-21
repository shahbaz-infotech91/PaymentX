package com.paymentx.controlcenter.dto.postgres;

import java.time.OffsetDateTime;

/**
 * ENGLISH: One real row from paymentx_validation.participant. What it
 * does: mirrors that table's real columns exactly (id, bank_id,
 * legal_name, status, onboarded_at, updated_at) - verified live
 * against the actual schema, not guessed. Why it exists: the read-only
 * Postgres layer's Participants domain response shape. How it will
 * communicate with the backend: returned by ParticipantRepository,
 * paginated by ParticipantsController.
 *
 * HINGLISH: paymentx_validation.participant ki ek real row. Ye kya
 * karti hai: us table ke real columns ko exactly mirror karta hai (id,
 * bank_id, legal_name, status, onboarded_at, updated_at) - live actual
 * schema ke against verify kiya gaya, guess nahi kiya gaya. Ye
 * dashboard me kyu hai: read-only Postgres layer ke Participants
 * domain ka response shape. Backend se kaise connect hogi:
 * ParticipantRepository ise return karta hai, ParticipantsController
 * ise paginate karta hai.
 */
public record ParticipantSummary(
        Long id,
        String bankId,
        String legalName,
        String status,
        OffsetDateTime onboardedAt,
        OffsetDateTime updatedAt
) {
}
