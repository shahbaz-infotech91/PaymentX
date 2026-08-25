package com.paymentx.agent.registry;

/**
 * Operational risk classification for an agent as a whole - distinct from, and coarser than,
 * MCP Gateway's own per-tool {@code ToolRiskLevel} (that risk level belongs to a single tool's
 * blast radius; this one belongs to an agent's overall investigative scope, useful for future
 * operational decisions like which agents warrant closer monitoring or a human-approval workflow
 * once one exists - Phase 4.1 does not build that workflow, only leaves a place to record the
 * classification).
 */
public enum AgentRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
