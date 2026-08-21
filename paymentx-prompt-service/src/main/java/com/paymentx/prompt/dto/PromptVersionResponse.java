package com.paymentx.prompt.dto;

import com.paymentx.prompt.entity.PromptStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * English:
 * The wire shape of one PromptVersion - never the JPA entity itself
 * (PromptController/PromptService never leak PromptVersion or
 * PromptTemplate outward, matching every other PaymentX service's own
 * DTO-not-entity convention, e.g. Routing Service's RouteRuleResponse).
 * Why it exists: response shape for GET .../versions, GET
 * .../versions/{version}, POST .../versions, POST .../activate, POST
 * .../deactivate - Step 3/8 of the Phase 3.2 brief.
 * How it communicates with other components: built by
 * PromptVersionMapper from a real PromptVersion row; returned inside
 * ApiResponse&lt;PromptVersionResponse&gt; by PromptController.
 *
 * Hinglish:
 * Ek PromptVersion ka wire shape - kabhi JPA entity khud nahi
 * (PromptController/PromptService kabhi PromptVersion ya
 * PromptTemplate bahar leak nahi karte, har doosri PaymentX service ke
 * apne DTO-not-entity convention se match karte hue, jaise Routing
 * Service ka RouteRuleResponse).
 * Ye kyu hai: GET .../versions, GET .../versions/{version}, POST
 * .../versions, POST .../activate, POST .../deactivate ke liye response
 * shape - Phase 3.2 brief ka Step 3/8.
 * Dusre components se kaise communicate karta hai: PromptVersionMapper
 * ek real PromptVersion row se ise banata hai; PromptController ise
 * ApiResponse&lt;PromptVersionResponse&gt; ke andar return karta hai.
 */
public record PromptVersionResponse(
        String promptKey,
        Integer versionNumber,
        String content,
        PromptStatus status,
        List<PromptVariable> variables,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy
) {
}
