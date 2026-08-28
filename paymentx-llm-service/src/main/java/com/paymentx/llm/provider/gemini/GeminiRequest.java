package com.paymentx.llm.provider.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * The real request body shape POST /v1beta/models/{model}:generateContent
 * expects (Gemini API, verified against ai.google.dev/api/generate-content
 * before implementation, per this phase's model-selection requirement).
 * Mirrors AnthropicLlmProvider's isolation boundary - these records are the
 * ONLY place in this service that model a Gemini-specific wire shape;
 * LlmService/LlmServiceImpl/LlmController never see them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiRequest(
        List<GeminiContent> contents,
        GeminiContent systemInstruction,
        // Phase 5.2 fix - Gemini's real generateContent contract puts function declarations in a
        // top-level `tools` field (tools: [{functionDeclarations: [...]}]), never inside
        // generationConfig (verified against ai.google.dev/api/generate-content; the prior
        // GeminiGenerationConfig.toolDeclrations field was both misspelled and in the wrong
        // location - Gemini rejected it outright with "Unknown name ... at 'generation_config'").
        List<GeminiTool> tools,
        GeminiGenerationConfig generationConfig
) {

    record GeminiContent(String role, List<GeminiPart> parts) {
    }

    record GeminiPart(String text) {
    }

    record GeminiTool(List<Map<String, Object>> functionDeclarations) {
    }

    record GeminiGenerationConfig(Long maxOutputTokens, Double temperature) {
    }
}
