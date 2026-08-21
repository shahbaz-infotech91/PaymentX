package com.paymentx.llm.dto;

/**
 * English:
 * Real token-usage numbers echoed back from the provider's own response
 * (com.anthropic.models.messages.Usage) - never estimated/computed
 * client-side. `cacheCreationInputTokens`/`cacheReadInputTokens` are
 * nullable because the provider only returns them when prompt caching
 * was actually in play for that specific call (this service does not
 * request prompt caching in Phase 3.3 - see AnthropicLlmProvider - so
 * in practice both are usually null today; the fields exist so a future
 * phase enabling cache_control doesn't need a response-shape change).
 * Why it exists: Step 15's "track usage" requirement.
 * How it communicates with other components: nested inside
 * GenerateResponse; built by AnthropicLlmProvider from the real
 * com.anthropic.models.messages.Usage on every successful call.
 *
 * Hinglish:
 * Provider ke apne response (com.anthropic.models.messages.Usage) se
 * echo hue real token-usage numbers - kabhi client-side estimate/
 * compute nahi kiye jaate. `cacheCreationInputTokens`/
 * `cacheReadInputTokens` nullable hain kyunki provider inhe sirf tab
 * return karta hai jab us specific call ke liye prompt caching actually
 * in play thi (ye service Phase 3.3 me prompt caching request nahi
 * karti - AnthropicLlmProvider dekho - isliye practically dono aaj
 * usually null hote hain; fields isliye exist karte hain taaki ek
 * future phase jo cache_control enable kare use response-shape change
 * na karni pade).
 * Ye kyu hai: Step 15 ki "usage track karo" requirement.
 * Dusre components se kaise communicate karta hai: GenerateResponse ke
 * andar nested hai; AnthropicLlmProvider ise har successful call par
 * real com.anthropic.models.messages.Usage se banata hai.
 */
public record LlmUsage(
        long inputTokens,
        long outputTokens,
        Long cacheCreationInputTokens,
        Long cacheReadInputTokens
) {
}
