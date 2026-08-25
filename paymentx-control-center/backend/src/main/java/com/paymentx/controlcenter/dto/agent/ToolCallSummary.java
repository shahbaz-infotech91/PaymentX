package com.paymentx.controlcenter.dto.agent;

import com.fasterxml.jackson.databind.JsonNode;

/** Phase 4.7 - one real MCP tool call's evidence, verbatim from Agent Orchestrator's own toolEvidence. */
public record ToolCallSummary(String tool, String status, JsonNode result) {
}
