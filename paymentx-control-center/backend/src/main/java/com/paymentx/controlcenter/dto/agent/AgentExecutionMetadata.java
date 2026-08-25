package com.paymentx.controlcenter.dto.agent;

/** Phase 4.7 - verbatim from Agent Orchestrator's own ExecutionMetadata. */
public record AgentExecutionMetadata(int iterations, int toolCallCount, boolean ragUsed, long totalLatencyMs) {
}
