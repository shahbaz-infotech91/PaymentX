package com.paymentx.prompt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * English:
 * One declared variable a PromptVersion's content may reference via
 * {{name}}. Deliberately ONE shape used both as API input (nested
 * inside CreatePromptRequest/CreatePromptVersionRequest) and as the
 * value persisted in PromptVersion's JSON `variables` column (see that
 * entity) - a separate request-DTO/domain-object pair would be pure
 * duplication for a value this small, with no independent lifecycle of
 * its own (a variable definition IS the version it belongs to, it is
 * never queried or updated on its own - Step 5/7 of the Phase 3.2 brief
 * explicitly permits skipping a separate prompt_variable table when the
 * simpler design is safe, and it is here). `required` governs
 * PromptRenderer's behavior: true means a missing/blank value at render
 * time is a real MISSING_VARIABLE error; false means the variable is
 * genuinely optional and substitutes as an empty string when absent -
 * this is the "unless explicitly designed" exception Step 10 allows,
 * and it is spelled out here rather than left implicit.
 * Why it exists: strongly typed variable declarations instead of
 * accepting an untyped Map for the create/version request bodies (Step
 * 12's "use typed DTOs... do not accept arbitrary maps everywhere").
 * How it communicates with other components: nested in
 * CreatePromptRequest/CreatePromptVersionRequest (client input) and in
 * PromptVersionResponse.variables (server output); PromptRenderer reads
 * PromptVersion.getVariables() (this same shape) to validate a render
 * request's variables map.
 *
 * Hinglish:
 * Ek declared variable jise ek PromptVersion ka content {{name}} ke
 * through reference kar sakta hai. Jaan-boojh kar EK hi shape hai jo
 * API input (CreatePromptRequest/CreatePromptVersionRequest ke andar
 * nested) aur PromptVersion ke JSON `variables` column me persist hone
 * wali value (wo entity dekho) dono ke roop me use hoti hai - ek alag
 * request-DTO/domain-object pair itni chhoti value ke liye pure
 * duplication hoti, jiski apni koi independent lifecycle nahi hai (ek
 * variable definition WAHI version hai jiska ye hissa hai, ye kabhi
 * akele query ya update nahi hoti - Phase 3.2 brief ka Step 5/7
 * explicitly ek alag prompt_variable table skip karne ki ijazat deta
 * hai jab simpler design safe ho, aur yahan hai). `required`
 * PromptRenderer ka behavior govern karta hai: true ka matlab render
 * time par missing/blank value ek real MISSING_VARIABLE error hai;
 * false ka matlab variable genuinely optional hai aur absent hone par
 * empty string se substitute hoti hai - yehi "unless explicitly
 * designed" exception hai jo Step 10 allow karta hai, aur yahan implicit
 * chhodne ke bajaye explicitly likha gaya hai.
 * Ye kyu hai: create/version request bodies ke liye strongly typed
 * variable declarations, ek untyped Map accept karne ke bajaye (Step
 * 12 ka "typed DTOs use karo... har jagah arbitrary maps accept mat
 * karo").
 * Dusre components se kaise communicate karta hai: CreatePromptRequest/
 * CreatePromptVersionRequest (client input) me aur
 * PromptVersionResponse.variables (server output) me nested hai;
 * PromptRenderer PromptVersion.getVariables() (yahi shape) padhta hai
 * ek render request ke variables map ko validate karne ke liye.
 */
public record PromptVariable(

        @NotBlank(message = "variable name must not be blank")
        @Size(max = 64, message = "variable name must be at most 64 characters")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "variable name must start with a letter and contain only letters, digits, underscore")
        String name,

        boolean required,

        @Size(max = 256, message = "variable description must be at most 256 characters")
        String description
) {
}
