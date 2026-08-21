package com.paymentx.vector.entity;

/**
 * English:
 * The real lifecycle state of one ai_document row - Step 9/23 of the
 * Phase 3.5 brief's "lifecycle management" requirement. ACTIVE is the
 * normal, searchable state; ARCHIVED marks a document as retired
 * without physically deleting it (and its chunks/embeddings) - useful
 * for a superseded document version (Step 19: "do not accidentally
 * overwrite historical versions") that should stop appearing in search
 * results but must remain queryable for audit/reproducibility. There is
 * no third "DELETED" state - a genuine delete (Step 18) is a real SQL
 * DELETE with ON DELETE CASCADE onto chunks/embeddings, not a soft-
 * delete flag, matching this platform's existing convention (see
 * V1_0_0__create_ai_vector_tables.yaml's cascade FKs) - a document
 * table with both an ARCHIVED status AND a soft-delete flag would be
 * two independently-updatable "is this gone" signals that could drift
 * out of sync, the exact anti-pattern PromptTemplate's own javadoc
 * warns against for status columns.
 * Why it exists: Step 9's lifecycle-management requirement, satisfied
 * with the minimum states that are genuinely distinct and needed.
 * How it communicates with other components: VectorStoreServiceImpl's
 * search path filters to status = ACTIVE by default; AiDocumentRepository
 * persists/queries this column.
 *
 * Hinglish:
 * Ek ai_document row ka real lifecycle state - Phase 3.5 brief ka Step
 * 9/23 "lifecycle management" requirement. ACTIVE normal, searchable
 * state hai; ARCHIVED ek document ko retire mark karta hai bina use
 * physically delete kiye (aur uske chunks/embeddings) - ek superseded
 * document version ke liye useful hai (Step 19: "historical versions ko
 * accidentally overwrite mat karo") jise search results me aana band ho
 * jaana chahiye lekin audit/reproducibility ke liye queryable rehna
 * chahiye. Koi teesra "DELETED" state nahi hai - ek genuine delete
 * (Step 18) ek real SQL DELETE hai ON DELETE CASCADE ke saath
 * chunks/embeddings par, ek soft-delete flag nahi, is platform ke
 * existing convention se match karte hue
 * (V1_0_0__create_ai_vector_tables.yaml ke cascade FKs dekho) - ek
 * document table jisme dono ARCHIVED status AUR ek soft-delete flag ho
 * wo do independently-updatable "kya ye gaya" signals honge jo out of
 * sync drift kar sakte hain, exactly wahi anti-pattern jiske khilaaf
 * PromptTemplate ka apna javadoc status columns ke liye warn karta hai.
 * Ye kyu hai: Step 9 ki lifecycle-management requirement, minimum states
 * ke saath satisfy ki gayi jo genuinely distinct aur zaroori hain.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * ka search path default se status = ACTIVE tak filter karta hai;
 * AiDocumentRepository is column ko persist/query karta hai.
 */
public enum DocumentStatus {
    ACTIVE,
    ARCHIVED
}
