package com.paymentx.llm.provider;

import java.util.List;
import java.util.Map;

/**
 * English:
 * The internal, provider-agnostic request LlmServiceImpl passes to
 * whichever LlmProvider is configured - already fully resolved
 * (defaults applied, no nulls left for `model`/`maxTokens`) so
 * AnthropicLlmProvider never needs to know this platform's
 * configuration property names, and a second provider added in a later
 * phase could consume the exact same type without touching
 * LlmServiceImpl. Deliberately NOT the same type as the public
 * GenerateRequest DTO - that record is HTTP/validation-shaped
 * (nullable overrides, Jakarta Validation annotations); this one is
 * business-shaped (fully resolved values only).
 * Phase 5.2 addition: `tools` is an optional list of provider-neutral
 * tool definitions (name, description, input schema) that the provider
 * must map to its native API format for tool/function calling. If a
 * provider does not support native tool calling, it must handle the
 * tools explicitly rather than silently dropping them (see each
 * provider's generate() javadoc for the degradation strategy).
 * Why it exists: PAYMENTX_PHASE_3_ARCHITECTURE.md §8's
 * LlmService -> LlmProvider -> ProviderAdapter layering - the
 * application layer (LlmServiceImpl) must never touch a
 * provider-specific type (com.anthropic.models.messages.*), and a
 * provider adapter must never touch an HTTP-shaped DTO.
 * How it communicates with other components: built by LlmServiceImpl,
 * consumed by LlmProvider.generate (implemented by
 * AnthropicLlmProvider/GeminiLlmProvider/GroqLlmProvider/OpenAiLlmProvider).
 *
 * Hinglish:
 * LlmServiceImpl jo bhi LlmProvider configured hai use jo internal,
 * provider-agnostic request pass karta hai - already fully resolved
 * (defaults apply ho chuke, `model`/`maxTokens` ke liye koi null nahi
 * bacha) taaki AnthropicLlmProvider ko is platform ke configuration
 * property names kabhi jaanne ki zaroorat na pade, aur ek future phase
 * me add hone wala doosra provider bilkul isi type ko consume kar sake
 * bina LlmServiceImpl chhue. Jaan-boojh kar public GenerateRequest DTO
 * jaisa type NAHI hai - wo record HTTP/validation-shaped hai (nullable
 * overrides, Jakarta Validation annotations); ye business-shaped hai
 * (sirf fully resolved values).
 * Phase 5.2 addition: `tools` ek optional list hai provider-neutral
 * tool definitions (name, description, input schema) jo provider ko
 * apni native API format me map karna zaruri hai tool/function calling ke
 * liye. agar provider native tool calling support nahi karta, toh usko
 * tools ko explicitly handle karna chahiye - silently drop mat karein
 * (har provider ki generate() javadoc dekho degradation strategy ke liye).
 * Ye kyu hai: PAYMENTX_PHASE_3_ARCHITECTURE.md §8 ka
 * LlmService -> LlmProvider -> ProviderAdapter layering - application
 * layer (LlmServiceImpl) ko kabhi ek provider-specific type
 * (com.anthropic.models.messages.*) chhuna nahi chahiye, aur ek
 * provider adapter ko kabhi ek HTTP-shaped DTO chhuna nahi chahiye.
 * Dusre components se kaise communicate karta hai: LlmServiceImpl ise
 * banata hai, LlmProvider.generate (AnthropicLlmProvider dwara
 * implement) ise consume karta hai.
 */
public record LlmProviderRequest(
        String prompt,
        String systemPrompt,
        String model,
        long maxTokens,
        Double temperature,
        // Phase 5.2 - optional list of provider-neutral tool definitions.
        // Each tool contains name, description, and input schema (JSON-friendly map).
        // The provider must map these to its native API format.
        List<Map<String, Object>> tools
) {
    public LlmProviderRequest(String prompt, String systemPrompt, String model, long maxTokens, Double temperature) {
        this(prompt, systemPrompt, model, maxTokens, temperature, null);
    }
}
