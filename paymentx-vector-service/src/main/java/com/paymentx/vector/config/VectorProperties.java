package com.paymentx.vector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * English:
 * The single typed configuration surface for this service's search
 * bounds and default provider/model - binds every vector.* key from
 * application.yml. `defaultProvider`/`defaultModel` match Embedding
 * Service's own configured defaults (Phase 3.4's `embedding.openai.*`)
 * so a caller of POST /api/v1/vector/search does not have to repeat
 * them on every call when there is only one real provider/model in use
 * - an explicit per-request override is still honored (see
 * VectorSearchRequest.provider/model). `maxTopK` (Step 21 - "Do NOT
 * allow topK = 100000... protect the database from expensive
 * searches") is the live, tunable ceiling VectorStoreServiceImpl
 * enforces in addition to VectorSearchRequest's own fixed
 * @Max(100) annotation - the same two-layer bound pattern
 * EmbeddingRequest/EmbeddingProperties.maxInputLength established in
 * Phase 3.4. `defaultTopK` is what a request with no `topK` at all
 * resolves to.
 * Why it exists: Step 21's tunable, database-protecting topK ceiling,
 * and Step 3/8's provider/model defaulting.
 * How it communicates with other components: bound automatically via
 * @ConfigurationPropertiesScan on VectorServiceApplication; injected
 * into VectorStoreServiceImpl.
 *
 * Hinglish:
 * Is service ke search bounds aur default provider/model ke liye ek hi
 * typed configuration surface - application.yml ke har vector.* key ko
 * bind karta hai. `defaultProvider`/`defaultModel` Embedding Service ke
 * apne configured defaults (Phase 3.4 ke `embedding.openai.*`) se match
 * karte hain taaki POST /api/v1/vector/search ka ek caller unhe har
 * call par repeat na kare jab sirf ek hi real provider/model use me ho
 * - ek explicit per-request override ab bhi honor hota hai
 * (VectorSearchRequest.provider/model dekho). `maxTopK` (Step 21 -
 * "topK = 100000 allow mat karo... database ko expensive searches se
 * protect karo") wo live, tunable ceiling hai jise
 * VectorStoreServiceImpl VectorSearchRequest ke apne fixed @Max(100)
 * annotation ke alawa enforce karta hai - wahi two-layer bound pattern
 * jo Phase 3.4 me EmbeddingRequest/EmbeddingProperties.maxInputLength
 * ne establish kiya tha. `defaultTopK` wo hai jispar koi bhi request
 * jisme bilkul `topK` na ho resolve hoti hai.
 * Ye kyu hai: Step 21 ka tunable, database-protecting topK ceiling, aur
 * Step 3/8 ka provider/model defaulting.
 * Dusre components se kaise communicate karta hai:
 * VectorServiceApplication ke @ConfigurationPropertiesScan se
 * automatically bind hota hai; VectorStoreServiceImpl me inject hota
 * hai.
 */
@ConfigurationProperties(prefix = "vector")
@Data
public class VectorProperties {

    // Phase 3.5 vector database migration: defaults changed from openai/text-embedding-3-small/1536 to
    // match Phase 3.4's new local-provider default (see PAYMENTX_PHASE_3_4_LOCAL_EMBEDDING.md) - this is
    // the real, currently-configured Embedding Service, not a hypothetical one.
    private String defaultProvider = "local";
    private String defaultModel = "sentence-transformers/all-MiniLM-L6-v2";
    private int dimension = 384;
    private int defaultTopK = 5;
    private int maxTopK = 100;
}
