package com.paymentx.embedding.dto;

import java.time.OffsetDateTime;

/**
 * English:
 * GET /api/v1/embeddings/health's real, honest response - matches LLM
 * Service's LlmHealthResponse pattern exactly: deliberately does NOT
 * make a real (billed) call to the provider to prove reachability (Step
 * 22 of the Phase 3.4 brief). `status` is one of three real, distinct
 * values - "SERVICE_UP" (the process is running but embedding.enabled
 * is false, or no api-key is bound - matches "NOT_CONFIGURED" naming
 * used elsewhere but scoped to this service's own vocabulary per Step
 * 22's example), "NOT_CONFIGURED" (embedding.enabled=false or api-key
 * unset), "CONFIGURED" (an api-key is present - does NOT mean it is
 * valid, only that an operator set one). `note` spells this limitation
 * out in the response body itself so a caller never mistakes CONFIGURED
 * for "verified working" - the only way to learn whether the configured
 * credentials/model actually work is a real POST /api/v1/embeddings
 * call, whose success/failure is itself the honest signal.
 * Why it exists: Step 22 - avoid a paid health-check call while still
 * giving operators a real, truthful signal.
 * How it communicates with other components: built by
 * EmbeddingServiceImpl.health(); returned inside
 * ApiResponse&lt;EmbeddingHealthResponse&gt; by
 * EmbeddingController.health.
 *
 * Hinglish:
 * GET /api/v1/embeddings/health ka real, honest response - LLM Service
 * ke LlmHealthResponse pattern se exactly match karta hai: jaan-boojh
 * kar provider tak reachability prove karne ke liye ek real (billed)
 * call NAHI karta (Phase 3.4 brief ka Step 22). `status` teen real,
 * distinct values me se ek hota hai - "SERVICE_UP" (process chal raha
 * hai lekin embedding.enabled false hai, ya koi api-key bound nahi hai
 * - "NOT_CONFIGURED" naming se match karta hai jo kahin aur use hoti
 * hai lekin Step 22 ke example ke hisaab se is service ki apni
 * vocabulary tak scoped hai), "NOT_CONFIGURED" (embedding.enabled=false
 * ya api-key unset), "CONFIGURED" (ek api-key present hai - iska matlab
 * ye NAHI ki wo valid hai, sirf itna ki ek operator ne ek set ki).
 * `note` is limitation ko response body me hi spell out karta hai
 * taaki ek caller kabhi CONFIGURED ko "verified working" na samjhe -
 * configured credentials/model actually kaam karte hain ya nahi ye
 * jaanne ka ek hi tareeka hai: ek real POST /api/v1/embeddings call,
 * jiski success/failure khud honest signal hai.
 * Ye kyu hai: Step 22 - ek paid health-check call avoid karte hue bhi
 * operators ko ek real, truthful signal dena.
 * Dusre components se kaise communicate karta hai:
 * EmbeddingServiceImpl.health() ise banata hai;
 * EmbeddingController.health ise
 * ApiResponse&lt;EmbeddingHealthResponse&gt; ke andar return karta hai.
 */
public record EmbeddingHealthResponse(
        String status,
        String provider,
        String configuredModel,
        int dimension,
        boolean apiKeyPresent,
        String note,
        OffsetDateTime checkedAt
) {
}
