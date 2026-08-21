package com.paymentx.prompt.dto;

import com.paymentx.prompt.entity.PromptType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * English:
 * The body of POST /api/v1/prompts - creates a new PromptTemplate plus
 * its version 1, as DRAFT (see PromptServiceImpl.createPrompt - a
 * freshly created version is never auto-activated, matching Step 4's
 * requirement that only an explicit activation call can make a version
 * live). `key` must be a stable, uppercase-with-underscores business
 * identifier (e.g. "PAYMENT_ERROR_ANALYSIS") - not a UUID a caller
 * would have to look up first. `variables` is exhaustive: PromptRenderer
 * rejects any {{placeholder}} in `content` that isn't declared here
 * (INVALID_PROMPT_CONTENT) and any render-time value supplied for a
 * name not declared here (UNKNOWN_VARIABLE).
 * Why it exists: Step 12 of the Phase 3.2 brief's exact example request
 * shape, typed rather than an arbitrary Map.
 * How it communicates with other components: bound from the request
 * body by PromptController.create(@Valid @RequestBody
 * CreatePromptRequest); consumed by PromptServiceImpl.createPrompt.
 *
 * Hinglish:
 * POST /api/v1/prompts ka body - ek naya PromptTemplate plus uska
 * version 1 create karta hai, DRAFT ke roop me (PromptServiceImpl.
 * createPrompt dekho - ek freshly created version kabhi auto-activate
 * nahi hota, Step 4 ki us requirement se match karte hue ki sirf ek
 * explicit activation call hi ek version ko live bana sakti hai). `key`
 * ek stable, uppercase-with-underscores business identifier hona
 * chahiye (jaise "PAYMENT_ERROR_ANALYSIS") - koi UUID nahi jise caller
 * ko pehle lookup karna pade. `variables` exhaustive hai:
 * PromptRenderer `content` me kisi bhi aise {{placeholder}} ko reject
 * karta hai jo yahan declare nahi hai (INVALID_PROMPT_CONTENT), aur
 * kisi bhi render-time value ko jo yahan declare na hue naam ke liye
 * diya gaya ho (UNKNOWN_VARIABLE).
 * Ye kyu hai: Phase 3.2 brief ke Step 12 ka exact example request
 * shape, ek arbitrary Map ke bajaye typed.
 * Dusre components se kaise communicate karta hai: request body se
 * PromptController.create(@Valid @RequestBody CreatePromptRequest) ise
 * bind karta hai; PromptServiceImpl.createPrompt ise consume karta hai.
 */
public record CreatePromptRequest(

        @NotBlank(message = "key is required")
        @Size(max = 128, message = "key must be at most 128 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "key must be UPPER_SNAKE_CASE, starting with a letter")
        String key,

        @NotBlank(message = "name is required")
        @Size(max = 256)
        String name,

        @Size(max = 1024)
        String description,

        @NotNull(message = "type is required")
        PromptType type,

        @NotBlank(message = "content is required")
        @Size(max = 20000, message = "content must be at most 20000 characters")
        String content,

        @NotNull(message = "variables must not be null (use an empty array if the prompt takes none)")
        @Valid
        List<PromptVariable> variables
) {
}
