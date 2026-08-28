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
        Double temperature,
        // Phase 5.2 - OpenAI-shaped tool declarations, same {type:"function", function:{name,
        // description, parameters}} wire shape GroqRequest already models (Groq's API is a
        // documented drop-in for OpenAI's own Chat Completions format). Null (omitted, via
        // @JsonInclude NON_NULL above) when no tools are supplied.
        List<java.util.Map<String, Object>> tools,
        // Only ever set to "auto" (never sent when tools is null/empty) - matches GroqRequest's
        // identical field, same reasoning: an OpenAI-compatible API rejects a tool call the model
        // attempts when tool_choice is absent/inconsistent with the tools present.
        @JsonProperty("tool_choice") String toolChoice
) {

    record OpenAiMessage(String role, String content) {
    }
}
