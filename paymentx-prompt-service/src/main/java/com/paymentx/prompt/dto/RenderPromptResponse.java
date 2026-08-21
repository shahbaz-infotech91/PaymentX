package com.paymentx.prompt.dto;

import java.util.List;

/**
 * English:
 * The result of rendering one PromptVersion's content with a caller's
 * variable values substituted in - Prompt Service's actual deliverable
 * to a future consumer (LLM Service, Phase 3.3). `renderedContent` is
 * plain text, never anything a downstream component would need to
 * further evaluate/execute (see PromptRenderer's javadoc: no SpEL, no
 * scripting - the substitution is pure string replacement). `variablesUsed`
 * lists exactly which declared variable names were actually substituted
 * into this render (useful for a caller/log line to confirm what went
 * into a given AI request without re-parsing the template itself).
 * Why it exists: Step 13 of the Phase 3.2 brief's exact example
 * response shape.
 * How it communicates with other components: built by
 * PromptServiceImpl.renderPrompt from PromptRenderer's output; returned
 * inside ApiResponse&lt;RenderPromptResponse&gt; by
 * PromptController.render - this IS the contract Phase 3.3's LLM
 * Service will call and consume `renderedContent` from (see
 * PAYMENTX_PHASE_3_ARCHITECTURE.md §4's "Rendered Prompt -> Future LLM
 * Service" flow).
 *
 * Hinglish:
 * Ek PromptVersion ke content ko caller ki variable values substitute
 * karke render karne ka result - Prompt Service ka actual deliverable
 * ek future consumer (LLM Service, Phase 3.3) ke liye. `renderedContent`
 * plain text hai, kabhi aisi cheez nahi jise ek downstream component ko
 * aage evaluate/execute karna pade (PromptRenderer ka javadoc dekho: na
 * SpEL, na scripting - substitution pure string replacement hai).
 * `variablesUsed` exactly wo declared variable names list karta hai jo
 * is render me actually substitute hue - ek caller/log line ke liye
 * useful hai ye confirm karne ke liye ki ek given AI request me kya
 * gaya, bina template ko dobara parse kiye.
 * Ye kyu hai: Phase 3.2 brief ke Step 13 ka exact example response
 * shape.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl.
 * renderPrompt ise PromptRenderer ke output se banata hai;
 * PromptController.render ise ApiResponse&lt;RenderPromptResponse&gt;
 * ke andar return karta hai - yehi wo contract hai jise Phase 3.3 ka
 * LLM Service call karega aur jisse `renderedContent` consume karega
 * (PAYMENTX_PHASE_3_ARCHITECTURE.md §4 ka "Rendered Prompt -> Future
 * LLM Service" flow dekho).
 */
public record RenderPromptResponse(
        String promptKey,
        Integer version,
        String renderedContent,
        List<String> variablesUsed
) {
}
