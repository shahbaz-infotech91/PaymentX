package com.paymentx.prompt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * English:
 * The body of POST /api/v1/prompts/{promptKey}/render. `version` is
 * optional - null means "render whatever version is currently ACTIVE"
 * (the normal case for AI Chat/future LLM Service, which should not
 * need to know a specific version number); an explicit value renders
 * exactly that version regardless of its status (DRAFT included - lets
 * an operator preview a not-yet-activated draft before promoting it;
 * ARCHIVED included - lets a caller reproduce a past AI answer exactly
 * as it was rendered at the time, see PromptVersion's javadoc). `variables`
 * values are plain strings only - deliberately not `Object`, so
 * PromptRenderer never needs to guess how to stringify an arbitrary
 * JSON value, and a caller cannot smuggle a nested object/array into a
 * template substitution.
 * Why it exists: Step 13 of the Phase 3.2 brief's exact example
 * request shape.
 * How it communicates with other components: bound by
 * PromptController.render; consumed by
 * PromptServiceImpl.renderPrompt -> PromptRenderer.render.
 *
 * Hinglish:
 * POST /api/v1/prompts/{promptKey}/render ka body. `version` optional
 * hai - null ka matlab hai "jo bhi version abhi ACTIVE hai use render
 * karo" (AI Chat/future LLM Service ke liye normal case, jise ek
 * specific version number jaanne ki zaroorat nahi honi chahiye); ek
 * explicit value uski status chahe kuch bhi ho exactly wahi version
 * render karta hai (DRAFT included - ek operator ko ek abhi-tak-
 * activate-na-hui draft preview karne deta hai promote karne se pehle;
 * ARCHIVED included - ek caller ko ek past AI answer ko exactly waisa
 * hi reproduce karne deta hai jaisa us waqt render hui thi,
 * PromptVersion ka javadoc dekho). `variables` values sirf plain
 * strings hain - jaan-boojh kar `Object` nahi, taaki PromptRenderer ko
 * kabhi guess na karna pade ki ek arbitrary JSON value ko kaise
 * stringify kare, aur ek caller ek nested object/array ko ek template
 * substitution me smuggle na kar sake.
 * Ye kyu hai: Phase 3.2 brief ke Step 13 ka exact example request
 * shape.
 * Dusre components se kaise communicate karta hai: PromptController.
 * render ise bind karta hai; PromptServiceImpl.renderPrompt ->
 * PromptRenderer.render ise consume karte hain.
 */
public record RenderPromptRequest(

        Integer version,

        @NotNull(message = "variables must not be null (use an empty object if the prompt takes none)")
        @Size(max = 100, message = "at most 100 variables may be supplied in a single render request")
        Map<String, String> variables
) {
}
