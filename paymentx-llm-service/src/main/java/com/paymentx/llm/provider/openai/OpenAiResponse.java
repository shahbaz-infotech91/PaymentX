package com.paymentx.llm.provider.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The real response body shape POST {base-url}/chat/completions returns on HTTP 200 - standard
 * OpenAI Chat Completions response shape, identical to GroqResponse's own (Groq's API is a
 * documented drop-in for this exact wire format). `choices` is never empty on a real 200; a
 * refused/filtered completion is represented via `finish_reason` on a real choice, not an empty
 * array - OpenAiLlmProvider's own refusal detection reads `finishReason` the same way
 * GroqLlmProvider does.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiResponse(
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
