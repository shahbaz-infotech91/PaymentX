package com.paymentx.agent.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * English:
 * The body of GET /api/v1/agent/health - real reachability of the four
 * downstream AI Platform services this agent orchestrates (RAG Service,
 * MCP Gateway, Prompt Service, LLM Service), mirroring RAG Service's
 * own RagHealthResponse pattern exactly (real UP/DOWN per dependency
 * probed via /actuator/health, never a fabricated status).
 * Why it exists: Step 40 item 29 (health endpoint), matching the
 * established per-service health-reporting convention.
 * How it communicates with other components: built by
 * orchestrator/AgentOrchestratorService.health(); returned by
 * controller/AgentController.health().
 *
 * Hinglish:
 * GET /api/v1/agent/health ka body - un char downstream AI Platform
 * services ki real reachability jinhe ye agent orchestrate karta hai
 * (RAG Service, MCP Gateway, Prompt Service, LLM Service), RAG Service
 * ke apne RagHealthResponse pattern ko exactly mirror karte hue (real
 * UP/DOWN per dependency, /actuator/health se probe kiya gaya, kabhi ek
 * fabricated status nahi).
 * Ye kyu hai: Step 40 item 29 (health endpoint), established per-
 * service health-reporting convention se match karte hue.
 * Dusre components se kaise communicate karta hai:
 * orchestrator/AgentOrchestratorService.health() dwara banaya jaata
 * hai; controller/AgentController.health() dwara return hota hai.
 */
public record AgentHealthResponse(
        String status,
        Map<String, String> dependencies,
        OffsetDateTime checkedAt
) {
}
