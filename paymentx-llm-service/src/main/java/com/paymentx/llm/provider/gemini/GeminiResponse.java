package com.paymentx.llm.provider.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The real response body shape POST /v1beta/models/{model}:generateContent
 * returns on HTTP 200 (Gemini API, verified against
 * ai.google.dev/api/generate-content before implementation). `candidates`
 * can legitimately be null/empty when the prompt itself was blocked before
 * generation (see `promptFeedback.blockReason`) - GeminiLlmProvider treats
 * that the same way AnthropicLlmProvider treats a REFUSAL stop reason: a
 * real HTTP-200 answer, not a thrown exception.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GeminiResponse(
        List<Candidate> candidates,
        PromptFeedback promptFeedback,
        UsageMetadata usageMetadata,
        String modelVersion
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(String role, List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PromptFeedback(String blockReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageMetadata(Long promptTokenCount, Long candidatesTokenCount, Long cachedContentTokenCount, Long totalTokenCount) {
    }
}
