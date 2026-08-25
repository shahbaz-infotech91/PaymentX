package com.paymentx.llm.dto;

/**
 * English:
 * The real, normalized result of one LLM call - the exact contract
 * Step 17/36 require. `content` is the provider's actual generated
 * text, joined from its real text content blocks - never a placeholder,
 * and empty string (not a fabricated sentence) when the model produced
 * no text (e.g. `refused=true`). `stopReason` is the provider's raw
 * stop reason string (end_turn/max_tokens/stop_sequence/tool_use/
 * pause_turn/refusal) passed through verbatim - a caller that cares
 * about "was this actually a complete answer" needs this, and this
 * service must not collapse it into a single "success" boolean that
 * would hide a truncated (max_tokens) or refused answer. `refused` is
 * `true` exactly when stopReason == "refusal" - Anthropic's API returns
 * HTTP 200 for a refusal (see the claude-api skill's Opus 5 refusal
 * note), so this field is the ONLY reliable signal a caller has that
 * the model declined to answer; treating a refusal as an error, or
 * silently returning it as if it were a normal answer, would both
 * violate Step 36's honesty rule - this field exists so callers can
 * treat it as its own distinct, truthful outcome.
 * Why it exists: Step 17 (response validation) and Step 36 (no fake
 * AI) simultaneously - see PAYMENTX_PHASE_3_3_LLM_SERVICE.md.
 * How it communicates with other components: built by LlmServiceImpl
 * from AnthropicLlmProvider's LlmProviderResult; returned inside
 * ApiResponse&lt;GenerateResponse&gt; by LlmController.generate - this
 * IS the contract AI Chat Service will consume once wired (Step 10).
 *
 * Hinglish:
 * Ek real LLM call ka real, normalized result - Step 17/36 ka exact
 * contract. `content` provider ka actual generated text hai, uske real
 * text content blocks se joined - kabhi ek placeholder nahi, aur khali
 * string (ek fabricated sentence nahi) jab model ne koi text produce na
 * kiya ho (jaise `refused=true`). `stopReason` provider ka raw stop
 * reason string hai (end_turn/max_tokens/stop_sequence/tool_use/
 * pause_turn/refusal), verbatim pass through kiya gaya - ek caller jise
 * "kya ye actually ek complete answer tha" jaanna hai use ye chahiye,
 * aur is service ko ise ek single "success" boolean me collapse nahi
 * karna chahiye jo ek truncated (max_tokens) ya refused answer chhupa
 * de. `refused` exactly `true` hota hai jab stopReason == "refusal" ho
 * - Anthropic ka API ek refusal ke liye HTTP 200 return karta hai
 * (claude-api skill ka Opus 5 refusal note dekho), isliye ye field hi
 * EK reliable signal hai jo ek caller ke paas hota hai ki model ne
 * jawab dene se mana kar diya; ek refusal ko error treat karna, ya use
 * silently ek normal answer jaisa return karna, dono Step 36 ke honesty
 * rule ko violate karenge - ye field isliye exist karta hai taaki
 * callers ise apna alag, truthful outcome treat kar sakein.
 * Ye kyu hai: Step 17 (response validation) aur Step 36 (no fake AI)
 * ek saath - PAYMENTX_PHASE_3_3_LLM_SERVICE.md dekho.
 * Dusre components se kaise communicate karta hai: LlmServiceImpl ise
 * AnthropicLlmProvider ke LlmProviderResult se banata hai;
 * LlmController.generate ise ApiResponse&lt;GenerateResponse&gt; ke
 * andar return karta hai - yehi wo contract hai jise AI Chat Service
 * ek baar wired hone par consume karegi (Step 10).
 */
public record GenerateResponse(
        String provider,
        String model,
        String content,
        String stopReason,
        boolean refused,
        LlmUsage usage,
        long latencyMs,
        // Phase 4.8.6 addition - additive, backward-compatible for every existing consumer:
        // paymentx-agent-orchestrator's own LlmServiceClient parses this response as a generic
        // JsonNode (never a strict-typed DTO), so a new field is simply ignored by any caller
        // that doesn't look for it. `provider`/`model` above already correctly reflect whichever
        // provider actually served the request (LlmServiceImpl.toResponse always builds this from
        // the real LlmProviderResult, fallback or not) - these two fields add the "why" on top.
        boolean fallbackUsed,
        String fallbackReason
) {
}
