package com.paymentx.llm.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * English:
 * The body of POST /api/v1/llm/generate - this service's only inbound
 * contract for real generation. `prompt` is the ALREADY-RENDERED text a
 * caller obtained from Prompt Service's POST /api/v1/prompts/{key}/
 * render (see RenderPromptResponse.renderedContent) - this service does
 * NOT call Prompt Service itself and does NOT accept a promptKey/
 * variables pair, per Step 9 of the Phase 3.3 brief: the target flow is
 * AI Chat -> Prompt Service -> Rendered Prompt -> LLM Service, so the
 * orchestration of "look up and render a template" is AI Chat Service's
 * job, not this service's. `model`/`maxTokens`/`temperature` are
 * optional per-request overrides of this service's configured defaults
 * (llm.anthropic.model/default-max-tokens) - null means "use the
 * configured default", never a silently-different hardcoded value.
 * Why it exists: Step 5/9's exact contract requirement.
 * How it communicates with other components: bound by
 * LlmController.generate; mapped by LlmServiceImpl into a
 * provider-agnostic LlmProviderRequest.
 *
 * Hinglish:
 * POST /api/v1/llm/generate ka body - is service ka real generation ke
 * liye ek hi inbound contract. `prompt` wo text hai jo ALREADY-RENDERED
 * hai, jise caller ne Prompt Service ke POST /api/v1/prompts/{key}/
 * render se liya hai (RenderPromptResponse.renderedContent dekho) - ye
 * service khud Prompt Service ko call NAHI karti aur promptKey/
 * variables pair accept NAHI karti, Phase 3.3 brief ke Step 9 ke
 * hisaab se: target flow hai AI Chat -> Prompt Service -> Rendered
 * Prompt -> LLM Service, isliye "ek template lookup aur render karo"
 * wala orchestration AI Chat Service ka kaam hai, is service ka nahi.
 * `model`/`maxTokens`/`temperature` optional per-request overrides hain
 * is service ke configured defaults (llm.anthropic.model/default-max-
 * tokens) ke - null ka matlab hai "configured default use karo", kabhi
 * ek silently-different hardcoded value nahi.
 * Ye kyu hai: Step 5/9 ka exact contract requirement.
 * Dusre components se kaise communicate karta hai: LlmController.
 * generate ise bind karta hai; LlmServiceImpl ise ek provider-agnostic
 * LlmProviderRequest me map karta hai.
 */
public record GenerateRequest(

        @NotBlank(message = "prompt must not be blank")
        @Size(max = 100_000, message = "prompt must be at most 100,000 characters")
        String prompt,

        @Size(max = 10_000, message = "systemPrompt must be at most 10,000 characters")
        String systemPrompt,

        @Size(max = 200, message = "model must be at most 200 characters")
        String model,

        @Min(value = 1, message = "maxTokens must be at least 1")
        @Max(value = 64_000, message = "maxTokens must be at most 64,000")
        Integer maxTokens,

        @DecimalMin(value = "0.0", message = "temperature must be at least 0.0")
        @DecimalMax(value = "1.0", message = "temperature must be at most 1.0")
        Double temperature
) {
}
