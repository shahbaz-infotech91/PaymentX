package com.paymentx.vector.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * English:
 * The real result of a store/upsert call - `created` distinguishes a
 * brand-new document (Step 17: "preserve audit timestamps where
 * appropriate" - an upsert of an existing (documentKey, documentVersion)
 * keeps the original `createdAt`, only `updatedAt` moves) from an
 * update to an existing (documentKey, documentVersion) pair, so a
 * caller can tell idempotent re-ingestion apart from first-time
 * ingestion without a second lookup call.
 * Why it exists: Step 15/17's store contract needs a real acknowledgment
 * shape, not just an HTTP 200.
 * How it communicates with other components: built by
 * VectorStoreServiceImpl.storeDocument; returned inside
 * ApiResponse&lt;StoreDocumentResponse&gt; by
 * VectorController.storeDocument.
 *
 * Hinglish:
 * Ek store/upsert call ka real result - `created` ek bilkul naye
 * document (Step 17: "audit timestamps jahan appropriate ho preserve
 * karo" - ek existing (documentKey, documentVersion) ka upsert original
 * `createdAt` rakhta hai, sirf `updatedAt` badalta hai) ko ek existing
 * (documentKey, documentVersion) pair ke update se distinguish karta
 * hai, taaki ek caller idempotent re-ingestion ko first-time ingestion
 * se bina ek doosri lookup call ke bata sake.
 * Ye kyu hai: Step 15/17 ke store contract ko ek real acknowledgment
 * shape chahiye, sirf ek HTTP 200 nahi.
 * Dusre components se kaise communicate karta hai:
 * VectorStoreServiceImpl.storeDocument ise banata hai;
 * VectorController.storeDocument ise
 * ApiResponse&lt;StoreDocumentResponse&gt; ke andar return karta hai.
 */
public record StoreDocumentResponse(
        UUID documentId,
        String documentKey,
        String documentVersion,
        boolean created,
        int chunkCount,
        int embeddingCount,
        OffsetDateTime updatedAt
) {
}
