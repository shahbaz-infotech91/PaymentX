/**
 * Phase 4.7 - the real API client for the AI Agent Control Center. Calls the real backend
 * endpoints (GET /api/v1/agents, POST /api/v1/agents/execute, GET /api/v1/agents/executions,
 * GET /api/v1/agents/executions/{id}) through the same axiosClient every other service file uses
 * (dashboard-token interceptor, correlation ID handling, error normalization all inherited, never
 * reimplemented here) - mirrors aiService.ts's own established pattern for this domain, kept as a
 * separate file/route because this is a distinct feature (any registered agent, not just chat)
 * from the existing AI Assistant (aiService.ts, untouched by this phase).
 */
import { axiosClient, toApiError } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'
import type { PageResponse } from './postgresService'
import type {
  AgentExecuteRequest,
  AgentExecuteResponse,
  AgentExecutionDetail,
  AgentExecutionFilterParams,
  AgentExecutionSummary,
  AgentSummary,
} from '../types/agent'
// Same reasoning as aiService.ts's own AI_CHAT_TIMEOUT_MS - a real agent execution runs Agent
// Orchestrator's full bounded loop (LLM planning, MCP tool calls, RAG retrieval), which can
// legitimately take tens of seconds. Control Center backend's own readTimeoutMs is 90000ms.
const AGENT_EXECUTE_TIMEOUT_MS = 95000

export async function fetchAgents(): Promise<AgentSummary[]> {
  const response = await axiosClient.get<ApiResponse<AgentSummary[]>>('/api/v1/agents')
  return response.data.data ?? []
}

export async function executeAgent(request: AgentExecuteRequest): Promise<AgentExecuteResponse> {
  const response = await axiosClient.post<ApiResponse<AgentExecuteResponse>>('/api/v1/agents/execute', request, {
    timeout: AGENT_EXECUTE_TIMEOUT_MS,
  })
  if (!response.data.data) {
    throw new Error('Backend returned an empty agent execution payload.')
  }
  return response.data.data
}

export async function fetchExecutionHistory(
  page: number,
  size: number,
  filter: AgentExecutionFilterParams,
): Promise<PageResponse<AgentExecutionSummary>> {
  const response = await axiosClient.get<ApiResponse<PageResponse<AgentExecutionSummary>>>('/api/v1/agents/executions', {
    params: { page, size, ...filter },
  })
  return response.data.data ?? { content: [], page, size, totalElements: 0 }
}

export async function fetchExecutionDetail(executionId: string): Promise<AgentExecutionDetail> {
  const response = await axiosClient.get<ApiResponse<AgentExecutionDetail>>(
    `/api/v1/agents/executions/${encodeURIComponent(executionId)}`,
  )
  if (!response.data.data) {
    throw new Error('Backend returned an empty execution detail payload.')
  }
  return response.data.data
}

/** Same honest-classification convention as aiService.ts's classifyAiError - the real errorCode/HTTP status, never guessed. */
export function classifyAgentError(error: unknown): string {
  const apiError = toApiError(error)
  if (apiError.errorCode === 'AI_NOT_CONFIGURED') return 'AI Agent Control Center is not configured for this environment yet.'
  if (apiError.errorCode === 'VALIDATION_ERROR') return apiError.message || 'That request could not be sent as written.'
  if (apiError.errorCode === 'EXECUTION_NOT_FOUND') return 'No execution was found with that ID.'
  if (apiError.errorCode === 'TIMEOUT') return 'The request took too long to respond. Please try again.'
  return apiError.message || 'An unexpected error occurred.'
}
