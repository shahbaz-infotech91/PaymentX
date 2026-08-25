package com.paymentx.llm.provider.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The real response body shape POST {base-url}/chat/completions returns on HTTP 200 - standard
 * OpenAI Chat Completions response shape (verified against console.groq.com/docs/api-reference
 * before implementation). `choices` is never empty on a real 200 (unlike Gemini's `candidates`,
 * which can legitimately be empty for a blocked prompt) - Groq/OpenAI-shaped APIs represent a
 * refused/filtered completion via `finish_reason` on a real choice, not an empty array, so
 * GroqLlmProvider's own REFUSAL detection reads `finishReason` the same way
 * GeminiLlmProvider reads `candidate.finishReason()`.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GroqResponse(
        String model,
        List<Choice> choices,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("prompt_tokens") Long promptTokens,
            @JsonProperty("completion_tokens") Long completionTokens,
            @JsonProperty("total_tokens") Long totalTokens) {
    }
}
