package com.paymentx.embedding.dto;

import java.util.List;

/**
 * English:
 * The real, normalized result of one batch embedding call - the exact
 * contract Step 7 requires. `embeddings` has exactly as many items as
 * BatchEmbeddingRequest.texts had, in the same order (see
 * BatchEmbeddingItem's javadoc). `dimension`/`model`/`provider` are
 * batch-level (a single call always uses one configured model/provider
 * for every item in this phase - no per-item provider override exists).
 * Why it exists: Step 7/16 of the Phase 3.4 brief.
 * How it communicates with other components: built by
 * EmbeddingServiceImpl; returned inside
 * ApiResponse&lt;BatchEmbeddingResponse&gt; by
 * EmbeddingController.embedBatch.
 *
 * Hinglish:
 * Ek batch embedding call ka real, normalized result - Step 7 ka exact
 * contract. `embeddings` me exactly utne hi items hain jitne
 * BatchEmbeddingRequest.texts me the, usi order me
 * (BatchEmbeddingItem ka javadoc dekho). `dimension`/`model`/`provider`
 * batch-level hain (is phase me ek call hamesha har item ke liye ek hi
 * configured model/provider use karti hai - koi per-item provider
 * override exist nahi karta).
 * Ye kyu hai: Phase 3.4 brief ka Step 7/16.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * ise banata hai; EmbeddingController.embedBatch ise
 * ApiResponse&lt;BatchEmbeddingResponse&gt; ke andar return karta hai.
 */
public record BatchEmbeddingResponse(
        List<BatchEmbeddingItem> embeddings,
        int dimension,
        String model,
        String provider
) {
}
