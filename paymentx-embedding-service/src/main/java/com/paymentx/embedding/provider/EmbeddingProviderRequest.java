package com.paymentx.embedding.provider;

import java.util.List;

/**
 * English:
 * The internal, provider-agnostic request EmbeddingServiceImpl passes
 * to whichever EmbeddingProvider is configured - already fully resolved
 * (defaults applied, no null `model`) so OpenAiEmbeddingProvider never
 * needs to know this platform's configuration property names, matching
 * LLM Service's LlmProviderRequest pattern exactly. `texts` is always a
 * list, even for a single embedding call - EmbeddingServiceImpl.embed()
 * wraps its one string in a singleton list before calling
 * EmbeddingProvider.embed, so there is exactly one code path through
 * the provider layer for both single and batch calls (OpenAI's real API
 * accepts either shape identically - `input` is a string or an array of
 * strings - so this costs nothing and removes a second near-duplicate
 * adapter method).
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md-style
 * EmbeddingService -> EmbeddingProvider -> ProviderAdapter layering -
 * the application layer must never touch a provider-specific type, and
 * a provider adapter must never touch an HTTP-shaped DTO.
 * How it communicates with other components: built by
 * EmbeddingServiceImpl, consumed by EmbeddingProvider.embed
 * (implemented by OpenAiEmbeddingProvider).
 *
 * Hinglish:
 * EmbeddingServiceImpl jo bhi EmbeddingProvider configured hai use jo
 * internal, provider-agnostic request pass karta hai - already fully
 * resolved (defaults apply ho chuke, `model` ke liye koi null nahi
 * bacha) taaki OpenAiEmbeddingProvider ko is platform ke configuration
 * property names kabhi jaanne ki zaroorat na pade, LLM Service ke
 * LlmProviderRequest pattern se exactly match karte hue. `texts`
 * hamesha ek list hai, ek single embedding call ke liye bhi -
 * EmbeddingServiceImpl.embed() apne ek string ko ek singleton list me
 * wrap karta hai EmbeddingProvider.embed call karne se pehle, taaki
 * provider layer ke through single aur batch calls dono ke liye
 * exactly ek hi code path ho (OpenAI ka real API dono shapes identically
 * accept karta hai - `input` ek string ya strings ka ek array ho sakta
 * hai - isliye ye kuch cost nahi karta aur ek doosra near-duplicate
 * adapter method hata deta hai).
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md-style
 * EmbeddingService -> EmbeddingProvider -> ProviderAdapter layering -
 * application layer ko kabhi ek provider-specific type nahi chhuna
 * chahiye, aur ek provider adapter ko kabhi ek HTTP-shaped DTO nahi
 * chhuna chahiye.
 * Dusre components se kaise communicate karta hai: EmbeddingServiceImpl
 * ise banata hai, EmbeddingProvider.embed (OpenAiEmbeddingProvider dwara
 * implement) ise consume karta hai.
 */
public record EmbeddingProviderRequest(
        List<String> texts,
        String model
) {
}
