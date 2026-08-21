package com.paymentx.prompt.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * English:
 * The body of POST /api/v1/prompts/{promptKey}/versions - adds a new
 * DRAFT version to an existing PromptTemplate. The new version's
 * number is server-computed (existing max + 1, see
 * PromptServiceImpl.createVersion) - never client-supplied, so a
 * caller can never accidentally overwrite or skip a version number.
 * Why it exists: Step 4/8 of the Phase 3.2 brief - "Create new
 * version" as its own operation, distinct from "Create prompt" (which
 * creates the template AND its first version together).
 * How it communicates with other components: bound by
 * PromptController.createVersion; consumed by
 * PromptServiceImpl.createVersion.
 *
 * Hinglish:
 * POST /api/v1/prompts/{promptKey}/versions ka body - ek existing
 * PromptTemplate me ek naya DRAFT version add karta hai. Naye version
 * ka number server-computed hota hai (existing max + 1,
 * PromptServiceImpl.createVersion dekho) - kabhi client-supplied nahi,
 * taaki ek caller kabhi galti se ek version number overwrite ya skip
 * na kar sake.
 * Ye kyu hai: Phase 3.2 brief ka Step 4/8 - "Create new version" apna
 * ek alag operation hai, "Create prompt" se alag (jo template AUR
 * uska pehla version dono saath create karta hai).
 * Dusre components se kaise communicate karta hai: PromptController.
 * createVersion ise bind karta hai; PromptServiceImpl.createVersion
 * ise consume karta hai.
 */
public record CreatePromptVersionRequest(

        @NotBlank(message = "content is required")
        @Size(max = 20000, message = "content must be at most 20000 characters")
        String content,

        @NotNull(message = "variables must not be null (use an empty array if the prompt takes none)")
        @Valid
        List<PromptVariable> variables
) {
}
