package com.paymentx.embedding.provider;

/**
 * English:
 * The ProviderAdapter abstraction this service is built around:
 * EmbeddingService -> EmbeddingProvider -> ProviderAdapter, matching
 * LLM Service's LlmProvider pattern exactly (see that interface's
 * javadoc for the full rationale - it applies identically here).
 * Exactly one implementation exists in Phase 3.4
 * (OpenAiEmbeddingProvider, the one real provider this phase chose per
 * Step 3 of the brief) but every call site depends on this interface,
 * not that class - adding a second embedding provider later means
 * writing a second implementation, not touching EmbeddingServiceImpl,
 * EmbeddingController, or any DTO.
 * Why it exists: Step 2/3 - "do NOT tightly couple PaymentX to one
 * embedding provider... architecture must allow another embedding
 * provider to be added later without rewriting business logic."
 * How it communicates with other components: injected into
 * EmbeddingServiceImpl as a plain Spring bean (single implementation on
 * the classpath today, so no @Qualifier/selection logic is needed yet).
 *
 * Hinglish:
 * Is service ka ProviderAdapter abstraction jispar ye poori tarah bani
 * hai: EmbeddingService -> EmbeddingProvider -> ProviderAdapter, LLM
 * Service ke LlmProvider pattern se exactly match karte hue (poore
 * rationale ke liye us interface ka javadoc dekho - ye yahan bhi
 * identically apply hota hai). Phase 3.4 me exactly ek implementation
 * exist karti hai (OpenAiEmbeddingProvider, wo ek real provider jo is
 * phase ne brief ke Step 3 ke hisaab se choose kiya) lekin har call
 * site is interface par depend karta hai, us class par nahi - baad me
 * ek doosra embedding provider add karne ka matlab hoga ek doosri
 * implementation likhna, EmbeddingServiceImpl, EmbeddingController, ya
 * kisi DTO ko na chhuna.
 * Ye kyu hai: Step 2/3 - "PaymentX ko ek embedding provider se tightly
 * couple mat karo... architecture ko ek doosra embedding provider baad
 * me add karne dena chahiye business logic rewrite kiye bina."
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * me ek plain Spring bean ke roop me inject hota hai (aaj classpath par
 * ek hi implementation hai, isliye abhi koi @Qualifier/selection logic
 * ki zaroorat nahi).
 */
public interface EmbeddingProvider {

    String providerName();

    int dimension();

    /**
     * Phase 3.4 local-embedding migration addition. The model name this provider is currently configured
     * to use, known statically (no network/inference call) - lets EmbeddingServiceImpl.health() report a
     * real configured-model value for whichever provider is active without needing to know that provider's
     * concrete type or its EmbeddingProperties sub-section (was previously read directly off
     * properties.getOpenai(), hardcoding this service to one provider - see Step 5 of the local-embedding
     * migration brief: "architecture should remain provider-independent").
     */
    String configuredModel();

    /**
     * Phase 3.4 local-embedding migration addition. Whether this provider can currently serve a real
     * embedding request, checked cheaply (no billed/network call for a remote provider; a real in-memory
     * check for a local one - LocalEmbeddingProvider returns true only once its model has actually finished
     * loading). Generalizes what EmbeddingServiceImpl.health() used to compute itself by reading
     * properties.getOpenai().getApiKey() directly - that was correct for exactly one provider and would
     * have been meaningless for a provider with no API key at all.
     */
    boolean isReady();

    EmbeddingProviderResult embed(EmbeddingProviderRequest request);
}
