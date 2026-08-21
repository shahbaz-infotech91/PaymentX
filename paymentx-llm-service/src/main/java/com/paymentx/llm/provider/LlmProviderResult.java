package com.paymentx.llm.provider;

/**
 * English:
 * The internal, provider-agnostic result AnthropicLlmProvider (or any
 * future ProviderAdapter) returns to LlmServiceImpl - the mirror of
 * LlmProviderRequest. Built entirely from the real
 * com.anthropic.models.messages.Message the SDK returned (content,
 * stopReason, refused, token counts, model actually used - which may
 * differ from the requested alias) - never from a template or default.
 * `latencyMs` is measured around the single blocking SDK call inside
 * AnthropicLlmProvider, before Resilience4j retry/circuit-breaker
 * overhead is added on top, so it reflects the real provider round
 * trip a caller cares about for cost/performance analysis.
 * Why it exists: same provider-abstraction layering reason as
 * LlmProviderRequest - see that class's javadoc.
 * How it communicates with other components: returned by
 * LlmProvider.generate; mapped by LlmServiceImpl into the public
 * GenerateResponse DTO.
 *
 * Hinglish:
 * AnthropicLlmProvider (ya koi future ProviderAdapter) LlmServiceImpl
 * ko jo internal, provider-agnostic result return karta hai -
 * LlmProviderRequest ka mirror. Poora ka poora us real
 * com.anthropic.models.messages.Message se banaya gaya hai jo SDK ne
 * return kiya (content, stopReason, refused, token counts, actually
 * use hua model - jo requested alias se alag ho sakta hai) - kabhi ek
 * template ya default se nahi. `latencyMs` AnthropicLlmProvider ke
 * andar us ek blocking SDK call ke around measure hota hai, Resilience4j
 * retry/circuit-breaker overhead upar add hone se pehle, taaki wo us
 * real provider round trip ko reflect kare jo ek caller cost/
 * performance analysis ke liye jaanna chahta hai.
 * Ye kyu hai: LlmProviderRequest jaisa hi provider-abstraction layering
 * reason - us class ka javadoc dekho.
 * Dusre components se kaise communicate karta hai: LlmProvider.generate
 * dwara return hota hai; LlmServiceImpl ise public GenerateResponse DTO
 * me map karta hai.
 */
public record LlmProviderResult(
        String provider,
        String model,
        String content,
        String stopReason,
        boolean refused,
        long inputTokens,
        long outputTokens,
        Long cacheCreationInputTokens,
        Long cacheReadInputTokens,
        long latencyMs
) {
}
