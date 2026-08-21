package com.paymentx.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * English:
 * The body of POST /api/v1/rag/query - Step 5's exact contract. `query`
 * is the user's real natural-language question, never pre-embedded (the
 * caller does not compute an embedding - see RagServiceImpl, which
 * calls Embedding Service itself, Step 8). `filters` passes straight
 * through to Vector Service's own metadata-filtering mechanism (Step
 * 9/20's Phase 3.5 JSONB containment filtering, not reimplemented here)
 * - values are restricted to simple scalars by RagServiceImpl's own
 * validation (not this DTO's Bean Validation, since Jakarta Validation
 * has no clean way to constrain a Map's value types) specifically to
 * prevent a caller from smuggling a nested object/array into a filter
 * that Vector Service's `@>` containment check would otherwise accept
 * unpredictably. `topK` is bounded both here (@Max, matching every
 * other bounded-request DTO's fixed API-boundary ceiling in this
 * platform) and again against the live configured `rag.max-top-k` in
 * RagServiceImpl (Step 11).
 * Why it exists: Step 5/7's exact query contract and validation
 * requirements.
 * How it communicates with other components: bound by
 * RagController.query; consumed by RagServiceImpl.query.
 *
 * Hinglish:
 * POST /api/v1/rag/query ka body - Step 5 ka exact contract. `query`
 * user ka real natural-language question hai, kabhi pre-embedded nahi
 * (caller khud embedding compute nahi karta - RagServiceImpl dekho, jo
 * khud Embedding Service call karta hai, Step 8). `filters` seedhe
 * Vector Service ke apne metadata-filtering mechanism tak pass through
 * hota hai (Step 9/20 ka Phase 3.5 JSONB containment filtering, yahan
 * reimplement nahi kiya gaya) - values RagServiceImpl ki apni validation
 * se simple scalars tak restricted hain (is DTO ke Bean Validation se
 * nahi, kyunki Jakarta Validation ke paas ek Map ke value types
 * constrain karne ka koi clean tareeka nahi hai) specifically taaki ek
 * caller ek nested object/array ko ek filter me smuggle na kar sake jise
 * Vector Service ka `@>` containment check warna unpredictably accept
 * kar leta. `topK` yahan bhi bounded hai (@Max, is platform ke har
 * doosre bounded-request DTO ke fixed API-boundary ceiling se match
 * karte hue) aur RagServiceImpl me live configured `rag.max-top-k` ke
 * against phir se (Step 11).
 * Ye kyu hai: Step 5/7 ka exact query contract aur validation
 * requirements.
 * Dusre components se kaise communicate karta hai: RagController.query
 * ise bind karta hai; RagServiceImpl.query ise consume karta hai.
 */
public record RagQueryRequest(

        @NotBlank(message = "query must not be blank")
        @Size(max = 2000, message = "query must be at most 2,000 characters")
        String query,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 20, message = "topK must be at most 20")
        Integer topK,

        Map<String, Object> filters
) {
}
