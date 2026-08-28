package com.paymentx.llm.provider.groq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The real request body shape POST {base-url}/chat/completions expects - Groq's API is a
 * deliberate, documented drop-in for OpenAI's Chat Completions wire format (verified against
 * console.groq.com/docs/api-reference before implementation, not invented/assumed). Mirrors
 * GeminiRequest's isolation boundary - this record is the ONLY place in this service that models
 * a Groq-specific wire shape; LlmService/LlmServiceImpl/LlmController never see it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record GroqRequest(
        String model,
        List<GroqMessage> messages,
        @JsonProperty("max_completion_tokens") Long maxCompletionTokens,
        Double temperature,
        // Phase 5.2 - OpenAI-shaped tool declarations (same {type:"function", function:{name,
        // description, parameters}} wire shape GeminiLlmProvider maps request.tools() into for
        // Gemini's own format). Null (omitted, via @JsonInclude NON_NULL above) when no tools are
        // supplied - matches this provider's existing no-tools request shape exactly.
        List<java.util.Map<String, Object>> tools,
        // Only ever set to "auto" (never sent when tools is null/empty) - Groq/OpenAI reject a
        // tool call the model attempts when tool_choice is absent (defaults to "none"), which is
        // exactly the failure this field exists to fix.
        @JsonProperty("tool_choice") String toolChoice
) {

    record GroqMessage(String role, String content) {
    }
}
