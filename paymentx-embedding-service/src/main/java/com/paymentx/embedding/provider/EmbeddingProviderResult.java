package com.paymentx.embedding.provider;

import java.util.List;

/**
 * English:
 * The internal, provider-agnostic result OpenAiEmbeddingProvider (or
 * any future ProviderAdapter) returns to EmbeddingServiceImpl - the
 * mirror of EmbeddingProviderRequest, matching LLM Service's
 * LlmProviderResult pattern exactly. `vectors` has exactly as many
 * entries as the request's `texts`, in the same order (index i of
 * `vectors` is the embedding for index i of `texts` - OpenAI's real
 * response already includes an `index` field per item, which
 * OpenAiEmbeddingProvider uses to re-sort defensively before returning,
 * rather than assuming array order is already correct). `latencyMs` is
 * measured around the single blocking HTTP call inside
 * OpenAiEmbeddingProvider, before Resilience4j retry/circuit-breaker
 * overhead is added on top.
 * Why it exists: same provider-abstraction layering reason as
 * EmbeddingProviderRequest - see that class's javadoc.
 * How it communicates with other components: returned by
 * EmbeddingProvider.embed; mapped by EmbeddingServiceImpl into the
 * public EmbeddingResponse/BatchEmbeddingResponse DTOs.
 *
 * Hinglish:
 * OpenAiEmbeddingProvider (ya koi future ProviderAdapter)
 * EmbeddingServiceImpl ko jo internal, provider-agnostic result return
 * karta hai - EmbeddingProviderRequest ka mirror, LLM Service ke
 * LlmProviderResult pattern se exactly match karte hue. `vectors` me
 * exactly utni hi entries hain jitni request ke `texts` me thi, usi
 * order me (index i of `vectors` index i of `texts` ka embedding hai -
 * OpenAI ka real response har item ke saath already ek `index` field
 * include karta hai, jise OpenAiEmbeddingProvider defensively re-sort
 * karne ke liye use karta hai return karne se pehle, ye assume karne ke
 * bajaye ki array order already sahi hai). `latencyMs`
 * OpenAiEmbeddingProvider ke andar us ek blocking HTTP call ke around
 * measure hota hai, Resilience4j retry/circuit-breaker overhead upar
 * add hone se pehle.
 * Ye kyu hai: EmbeddingProviderRequest jaisa hi provider-abstraction
 * layering reason - us class ka javadoc dekho.
 * Dusre components se kaise communicate karta hai: EmbeddingProvider.embed
 * dwara return hota hai; EmbeddingServiceImpl ise public
 * EmbeddingResponse/BatchEmbeddingResponse DTOs me map karta hai.
 */
public record EmbeddingProviderResult(
        String provider,
        String model,
        List<List<Float>> vectors,
        long latencyMs
) {
}
