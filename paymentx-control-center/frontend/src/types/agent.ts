// Phase 4.7 - TypeScript mirror of the backend's dto/agent/* records (see
// backend/src/main/java/.../dto/agent/). One place these shapes are defined on the frontend,
// matching one place on the backend - the same convention types/common.ts already established.

export interface AgentSummary {
  agentId: string
  name: string
  description: string
  version: string
  capabilities: string[]
  allowedTools: string[]
  riskLevel: string
  enabled: boolean
}

export interface AgentExecuteRequest {
  agentId: string
  userQuery: string
  paymentReference?: string
}

export interface RagSourceSummary {
  source: string | null
  score: number | null
}

export interface ToolCallSummary {
  tool: string | null
  status: string | null
  result: unknown
}

export interface AgentExecutionMetadata {
  iterations: number
  toolCallCount: number
  ragUsed: boolean
  totalLatencyMs: number
}

export interface AgentExecuteResponse {
  executionId: string | null
  correlationId: string | null
  agentId: string | null
  userQuery: string
  paymentReference: string | null
  status: string
  answer: string
  sources: RagSourceSummary[]
  toolsCalled: ToolCallSummary[]
  executionMetadata: AgentExecutionMetadata | null
  startedAt: string
  completedAt: string
  durationMs: number
  error: string | null
  // Phase 5 - LLM provider/fallback visibility (LlmProviderRouter, Phase 4.8.6). provider is
  // e.g. "gemini"/"anthropic"; fallbackUsed/fallbackReason are only set when the primary
  // provider failed and Agent Orchestrator's call was automatically retried on the fallback.
  provider: string | null
  fallbackUsed: boolean
  fallbackReason: string | null
}

export interface AgentExecutionSummary {
  executionId: string
  correlationId: string | null
  agentId: string | null
  userQuery: string | null
  paymentReference: string | null
  outcome: string | null
  timestamp: string
  durationMs: number | null
  toolCallCount: number | null
  ragUsed: boolean | null
  provider: string | null
  fallbackUsed: boolean | null
}

export interface AgentExecutionToolCall {
  tool: string | null
  status: string | null
}

export interface AgentExecutionDetail {
  executionId: string
  correlationId: string | null
  agentId: string | null
  userQuery: string | null
  paymentReference: string | null
  outcome: string | null
  answer: string | null
  ragSources: string[]
  toolsCalled: AgentExecutionToolCall[]
  startedAt: string | null
  completedAt: string | null
  durationMs: number | null
  iterations: number | null
  ragUsed: boolean | null
  error: string | null
  provider: string | null
  fallbackUsed: boolean | null
  fallbackReason: string | null
}

export interface AgentExecutionFilterParams {
  agentId?: string
  outcome?: string
  paymentReference?: string
  executionId?: string
  fromDate?: string
  toDate?: string
}
