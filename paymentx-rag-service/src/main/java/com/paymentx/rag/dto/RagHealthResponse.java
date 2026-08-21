package com.paymentx.rag.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * English:
 * GET /api/v1/rag/health's real, honest response - unlike LLM/
 * Embedding/Vector Service's own health endpoints (which report
 * config-presence, not reachability, to avoid a billed/expensive call),
 * RAG Service's health check CAN cheaply verify real reachability of
 * its four dependencies - a plain GET {baseUrl}/actuator/health per
 * dependency is free and fast (unlike a real embedding/LLM call), so
 * there is no honesty tension here the way there was for those
 * services. `dependencies` maps each of the four real service names
 * (embeddingService/vectorService/promptService/llmService) to a real
 * "UP"/"DOWN" - never a fabricated status if a probe fails, that
 * dependency is reported DOWN.
 * Why it exists: Step 26's health-endpoint requirement, giving an
 * operator real, per-dependency visibility instead of one opaque
 * boolean.
 * How it communicates with other components: built by
 * RagServiceImpl.health(); returned inside
 * ApiResponse&lt;RagHealthResponse&gt; by RagController.health.
 *
 * Hinglish:
 * GET /api/v1/rag/health ka real, honest response - LLM/Embedding/
 * Vector Service ke apne health endpoints ke ulat (jo config-presence
 * report karte hain, reachability nahi, ek billed/expensive call se
 * bachne ke liye), RAG Service ka health check apne char dependencies
 * ki real reachability cheaply verify KAR SAKTA hai - har dependency ke
 * liye ek plain GET {baseUrl}/actuator/health free aur fast hai (ek
 * real embedding/LLM call ke ulat), isliye yahan wo honesty tension
 * nahi hai jo un services ke liye tha. `dependencies` char real service
 * names (embeddingService/vectorService/promptService/llmService) ko
 * ek real "UP"/"DOWN" par map karta hai - kabhi ek fabricated status
 * nahi agar ek probe fail ho, wo dependency DOWN report hoti hai.
 * Ye kyu hai: Step 26 ki health-endpoint requirement, ek operator ko
 * real, per-dependency visibility deta hai ek opaque boolean ke bajaye.
 * Dusre components se kaise communicate karta hai: RagServiceImpl.health()
 * ise banata hai; RagController.health ise
 * ApiResponse&lt;RagHealthResponse&gt; ke andar return karta hai.
 */
public record RagHealthResponse(
        String status,
        Map<String, String> dependencies,
        OffsetDateTime checkedAt
) {
}
