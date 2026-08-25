package com.paymentx.llm.dto;

import java.time.OffsetDateTime;

/**
 * English:
 * GET /api/v1/llm/health's real, honest response - deliberately does
 * NOT make a real (billed) call to the provider to prove reachability
 * (Step 23 of the Phase 3.3 brief). `status` is CONFIGURED (an API key
 * is present - does NOT mean it is valid, only that an operator set
 * one) or NOT_CONFIGURED (no api-key bound - matches
 * ControlCenterProperties.Ai/AiComponentStatus's existing "honestly
 * report what we actually know" pattern from Phase 3.1, applied here).
 * `note` spells this limitation out in the response body itself so a
 * caller never mistakes CONFIGURED for "verified working" - the only
 * way to learn whether the configured credentials/model actually work
 * is a real POST /api/v1/llm/generate call, whose success/failure is
 * itself the honest signal, not a synthetic health probe that would
 * either cost money on every check or lie about reachability.
 * Why it exists: Step 23 - avoid a paid health-check call while still
 * giving operators a real, truthful signal.
 * How it communicates with other components: built by
 * LlmServiceImpl.health(); returned inside
 * ApiResponse&lt;LlmHealthResponse&gt; by LlmController.health -
 * consumed by Control Center's AiChatService once it aggregates this
 * service's status into AiHealthResponse.components (Step 10).
 *
 * Hinglish:
 * GET /api/v1/llm/health ka real, honest response - jaan-boojh kar
 * provider tak reachability prove karne ke liye ek real (billed) call
 * NAHI karta (Phase 3.3 brief ka Step 23). `status` CONFIGURED hota hai
 * (ek API key present hai - iska matlab ye NAHI ki wo valid hai, sirf
 * itna ki ek operator ne ek set ki) ya NOT_CONFIGURED (koi api-key
 * bound nahi - ControlCenterProperties.Ai/AiComponentStatus ke Phase
 * 3.1 wale existing "jo actually pata hai wahi honestly report karo"
 * pattern se match karta hai, yahan apply kiya gaya). `note` is
 * limitation ko response body me hi spell out karta hai taaki ek caller
 * kabhi CONFIGURED ko "verified working" na samjhe - configured
 * credentials/model actually kaam karte hain ya nahi ye jaanne ka ek hi
 * tareeka hai: ek real POST /api/v1/llm/generate call, jiski success/
 * failure khud honest signal hai, ek synthetic health probe nahi jo ya
 * toh har check par paisa kharch karega ya reachability ke baare me
 * jhooth bolega.
 * Ye kyu hai: Step 23 - ek paid health-check call avoid karte hue bhi
 * operators ko ek real, truthful signal dena.
 * Dusre components se kaise communicate karta hai:
 * LlmServiceImpl.health() ise banata hai; LlmController.health ise
 * ApiResponse&lt;LlmHealthResponse&gt; ke andar return karta hai - ek
 * baar Control Center ki AiChatService is service ka status
 * AiHealthResponse.components me aggregate karegi (Step 10) tab wahan
 * consume hoga.
 */
public record LlmHealthResponse(
        String status,
        String provider,
        String configuredModel,
        boolean apiKeyPresent,
        String note,
        OffsetDateTime checkedAt,
        // Phase 5 (Multi-Provider LLM Resilience Expansion) - additive. The fields above remain
        // exactly what they always were (the PRIMARY provider's own status only); this new field
        // reports the SAME apiKeyPresent/configuredModel signal for every registered provider
        // (gemini/anthropic/groq), not just the primary, so an operator can verify a fallback
        // provider (e.g. newly-added Groq) is actually configured without needing to temporarily
        // flip LLM_PROVIDER just to check. Never includes the key itself - same
        // apiKeyPresent-boolean-only pattern the primary fields already established.
        java.util.List<ProviderConfigStatus> allProviders
) {
    public record ProviderConfigStatus(String provider, boolean apiKeyPresent, String configuredModel) {
    }
}
