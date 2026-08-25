package com.paymentx.llm.provider.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The real request body shape POST {base-url}/chat/completions expects - OpenAI's own Chat
 * Completions wire format (the format Groq's own GroqRequest already deliberately mirrors).
 * Mirrors GroqRequest's isolation boundary - this record is the ONLY place in this service that
 * models an OpenAI-specific wire shape; LlmService/LlmServiceImpl/LlmController never see it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenAiRequest(
        String model,
        List<OpenAiMessage> messages,
        @JsonProperty("max_completion_tokens") Long maxCompletionTokens,
        Double temperature
) {

    record OpenAiMessage(String role, String content) {
    }
}
