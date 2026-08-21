package com.paymentx.controlcenter.dto.ai;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * ENGLISH: The response shape for GET /api/v1/ai/health. What it does:
 * reports one overall status string plus a per-component breakdown
 * (keys mirror the 7 AI Platform components named in the Phase 3.0
 * architecture audit - chatInterface, promptService, llmService,
 * embeddingService, ragService, mcpGateway, agentOrchestrator) using
 * AiComponentStatus so the frontend's AIStatus/AI Platform panel can
 * show real per-component state, not one coarse indicator. Why it
 * exists: this phase's Step 8 requirement literally, with the explicit
 * constraint "only expose components that actually exist or are
 * explicitly defined as future components... do not claim services are
 * healthy when they don't exist." How it will communicate with the
 * backend: built by AiChatService.health(), returned by
 * AiController's GET /api/v1/ai/health, consumed by the frontend's
 * useAiHealth hook.
 *
 * HINGLISH: GET /api/v1/ai/health ka response shape. Ye kya karti hai:
 * ek overall status string plus ek per-component breakdown report
 * karta hai (keys Phase 3.0 architecture audit me named 7 AI Platform
 * components ko mirror karti hain - chatInterface, promptService,
 * llmService, embeddingService, ragService, mcpGateway,
 * agentOrchestrator), AiComponentStatus use karte hue, taaki frontend
 * ka AIStatus/AI Platform panel real per-component state dikha sake,
 * ek coarse indicator nahi. Ye dashboard me kyu hai: is phase ka Step 8
 * requirement literally, is explicit constraint ke saath "sirf wahi
 * components expose karo jo actually exist karte hain ya explicitly
 * future components ke roop me define kiye gaye hain... jo services
 * exist hi nahi karti unhe healthy claim mat karo." Backend se kaise
 * connect hogi: AiChatService.health() ise banata hai, AiController ka
 * GET /api/v1/ai/health ise return karta hai, frontend ka useAiHealth
 * hook ise consume karta hai.
 */
public record AiHealthResponse(
        String status,
        Map<String, AiComponentStatus> components,
        OffsetDateTime timestamp
) {
}
