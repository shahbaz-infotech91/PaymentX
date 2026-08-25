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
        Double temperature
) {

    record GroqMessage(String role, String content) {
    }
}
