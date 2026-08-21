package com.paymentx.embedding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * English:
 * The body of POST /api/v1/embeddings/batch. `texts` order is
 * preserved end-to-end (Step 16 - "preserve item ordering") - each
 * item's position in this list is its `index` in
 * BatchEmbeddingResponse.embeddings, regardless of whether that item
 * succeeded or failed. Bounded by @Size(max = 50) as a fixed API-
 * boundary ceiling; the real, configured embedding.max-batch-size is
 * enforced by EmbeddingServiceImpl against the live config value,
 * matching EmbeddingRequest.text's identical two-layer bound rationale
 * (a tunable real limit plus a fixed hard ceiling that can never be
 * configured away to something pathological).
 * Why it exists: Step 6/16 of the Phase 3.4 brief - batch support,
 * justified by Phase 3.5's future document-ingestion need for
 * generating many embeddings without one HTTP round trip per chunk.
 * How it communicates with other components: bound by
 * EmbeddingController.embedBatch; mapped by EmbeddingServiceImpl into a
 * list of provider-agnostic EmbeddingProviderRequest.
 *
 * Hinglish:
 * POST /api/v1/embeddings/batch ka body. `texts` ka order end-to-end
 * preserve hota hai (Step 16 - "item ordering preserve karo") - is
 * list me har item ki position hi uska `index` hai
 * BatchEmbeddingResponse.embeddings me, chahe wo item succeed hua ho ya
 * fail. @Size(max = 50) se ek fixed API-boundary ceiling ke roop me
 * bounded hai; real, configured embedding.max-batch-size
 * EmbeddingServiceImpl dwara live config value ke against enforce hota
 * hai, EmbeddingRequest.text ke identical two-layer bound rationale se
 * match karte hue (ek tunable real limit plus ek fixed hard ceiling jo
 * kabhi kisi pathological cheez tak configure nahi ki ja sakti).
 * Ye kyu hai: Phase 3.4 brief ka Step 6/16 - batch support, Phase 3.5
 * ki future document-ingestion need se justified, taaki har chunk ke
 * liye ek HTTP round trip kiye bina kai embeddings generate ho sakein.
 * Dusre components se kaise communicate karta hai:
 * EmbeddingController.embedBatch ise bind karta hai; EmbeddingServiceImpl
 * ise provider-agnostic EmbeddingProviderRequest ki ek list me map
 * karta hai.
 */
public record BatchEmbeddingRequest(

        @NotEmpty(message = "texts must not be empty")
        @Size(max = 50, message = "at most 50 texts may be submitted in a single batch request")
        List<@NotBlank(message = "each text in the batch must not be blank") String> texts,

        @Size(max = 200, message = "model must be at most 200 characters")
        String model
) {
}
