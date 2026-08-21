/**
 * ENGLISH: The real API client for the Phase 3.1 AI Assistant domain.
 * What it does: calls the real backend endpoints
 * (POST /api/v1/ai/chat, GET /api/v1/ai/health) through the same
 * axiosClient every other service file uses (so authentication - the
 * dashboard-token interceptor - correlation ID handling, and timeout
 * behavior are inherited, never reimplemented here), and classifies any
 * failure into one honest AiErrorCategory instead of letting every
 * caller re-parse the raw ApiErrorResponse. sendAiChatMessage expects
 * to receive a real 503 (AI_NOT_CONFIGURED or AI_SERVICE_NOT_READY) on
 * every call today - see AiController.java/AiChatService.java - this is
 * not treated as a bug, it is the honest current state of the backend
 * contract, and classifyAiError() surfaces it as such rather than a
 * generic "request failed." Why it exists: keeps axios/error-shape
 * details out of hooks/useAiChat.ts and every component, matching every
 * other services/*.ts file's role in this app. How it will communicate
 * with the backend: real HTTP calls to AiController.
 *
 * HINGLISH: Phase 3.1 AI Assistant domain ke liye real API client. Ye
 * kya karti hai: real backend endpoints (POST /api/v1/ai/chat, GET
 * /api/v1/ai/health) ko usi axiosClient ke through call karta hai jo
 * har doosri service file use karti hai (taaki authentication -
 * dashboard-token interceptor - correlation ID handling, aur timeout
 * behavior inherit ho, yahan dobara implement na ho), aur kisi bhi
 * failure ko ek honest AiErrorCategory me classify karta hai taaki har
 * caller ko raw ApiErrorResponse dobara parse na karna pade.
 * sendAiChatMessage aaj har call par ek real 503 (AI_NOT_CONFIGURED ya
 * AI_SERVICE_NOT_READY) receive karne ki umeed karta hai -
 * AiController.java/AiChatService.java dekho - ise ek bug nahi maana
 * jaata, ye backend contract ka honest current state hai, aur
 * classifyAiError() ise waisa hi surface karta hai, ek generic "request
 * failed" ke bajaye. Ye dashboard me kyu hai: axios/error-shape details
 * ko hooks/useAiChat.ts aur har component se bahar rakhta hai, exactly
 * jaisa har doosri services/*.ts file ka role hai is app me. Backend se
 * kaise connect hogi: AiController ko real HTTP calls.
 */
import { axiosClient, toApiError } from '../api/axiosClient'
import type { ApiResponse } from '../types/common'
import type {
  AiChatRequest,
  AiChatResponse,
  AiClassifiedError,
  AiErrorCategory,
  AiHealthResponse,
} from '../types/ai'
import axios from 'axios'

// Phase 3.9 incremental re-validation fix: axiosClient's global default timeout (15000ms, tuned for
// fast CRUD-shaped calls) is far shorter than what a real AI chat round trip can legitimately take -
// Control Center backend's own ControlCenterProperties.Ai.readTimeoutMs is 90000ms, because Agent
// Orchestrator's real bounded loop (LLM planning, RAG retrieval which itself calls Embedding/Vector/
// Prompt/LLM Service, MCP tool calls) can genuinely take tens of seconds, longer still if Anthropic's
// own API is transiently overloaded and LLM Service's resilience retry kicks in. Discovered live this
// phase: a real, healthy, in-progress AI chat request was aborted client-side with "The request took
// too long to respond" at exactly 15s, while the real backend request was still working and later
// succeeded. This override applies ONLY to the AI chat call - every other axiosClient call (payments,
// participants, etc.) keeps the fast 15000ms default, since those really should fail fast.
const AI_CHAT_TIMEOUT_MS = 95000

export async function sendAiChatMessage(request: AiChatRequest): Promise<AiChatResponse> {
  const response = await axiosClient.post<ApiResponse<AiChatResponse>>('/api/v1/ai/chat', request, {
    timeout: AI_CHAT_TIMEOUT_MS,
  })
  if (!response.data.data) {
    throw new Error('Backend returned an empty AI chat payload.')
  }
  return response.data.data
}

export async function fetchAiHealth(): Promise<AiHealthResponse> {
  const response = await axiosClient.get<ApiResponse<AiHealthResponse>>('/api/v1/ai/health')
  if (!response.data.data) {
    throw new Error('Backend returned an empty AI health payload.')
  }
  return response.data.data
}

/**
 * Turns any failure from the calls above into one honest, specific
 * AiClassifiedError - the real errorCode/HTTP status the backend (or the
 * network) actually reported, never guessed. Enterprise-friendly messages
 * only; never a raw stack trace or Axios internal message.
 */
export function classifyAiError(error: unknown): AiClassifiedError {
  const apiError = toApiError(error)
  const httpStatus = axios.isAxiosError(error) ? error.response?.status : undefined

  const category: AiErrorCategory = (() => {
    if (apiError.errorCode === 'AI_NOT_CONFIGURED') return 'NOT_CONFIGURED'
    if (apiError.errorCode === 'AI_SERVICE_NOT_READY') return 'NOT_READY'
    // Phase 3.8: AiChatService now calls Agent Orchestrator instead of RAG Service directly (see
    // AiChatService.java's javadoc). Agent Orchestrator's normal outcomes (SUCCESS/INSUFFICIENT_CONTEXT/
    // REFUSED/DENIED/MAX_ITERATIONS/TIMEOUT) arrive as a real 200 response's `status` field, never as an
    // HTTP error - AgentOrchestratorService.execute() converts every planning/tool/RAG failure it can
    // recover from into one of those honest statuses internally (see that class's javadoc). These
    // AGENT_* branches only ever fire for the rare case an infrastructure failure escapes that
    // try/catch entirely (e.g. request validation, an unexpected bug) and reaches
    // GlobalExceptionHandler as a real HTTP error instead.
    if (
      apiError.errorCode === 'RAG_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'MCP_GATEWAY_UNAVAILABLE' ||
      apiError.errorCode === 'PROMPT_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'LLM_SERVICE_UNAVAILABLE'
    )
      return 'PROVIDER_UNAVAILABLE'
    if (apiError.errorCode === 'LLM_TIMEOUT') return 'TIMEOUT'
    if (apiError.errorCode === 'INVALID_REQUEST') return 'VALIDATION_ERROR'
    // Phase 3.6: RAG Service's own directly-emitted codes, kept for the rare case a caller still points
    // AiChatService straight at RAG Service in some future configuration, not because Agent Orchestrator
    // re-emits them verbatim (it normalizes them into its own AGENT_* codes above instead).
    if (
      apiError.errorCode === 'EMBEDDING_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'VECTOR_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'PROMPT_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'LLM_SERVICE_UNAVAILABLE' ||
      apiError.errorCode === 'CONTEXT_TOO_LARGE'
    )
      return 'PROVIDER_UNAVAILABLE'
    if (apiError.errorCode === 'LLM_TIMEOUT') return 'TIMEOUT'
    if (apiError.errorCode === 'INVALID_QUERY') return 'VALIDATION_ERROR'
    if (apiError.errorCode === 'LLM_NOT_CONFIGURED' || apiError.errorCode === 'LLM_CREDENTIALS_REJECTED') return 'PROVIDER_NOT_CONFIGURED'
    if (apiError.errorCode === 'LLM_RATE_LIMITED') return 'RATE_LIMITED'
    if (apiError.errorCode === 'LLM_PROVIDER_TIMEOUT') return 'TIMEOUT'
    if (
      apiError.errorCode === 'LLM_PROVIDER_UNAVAILABLE' ||
      apiError.errorCode === 'LLM_INVALID_REQUEST' ||
      apiError.errorCode === 'LLM_RESPONSE_INVALID' ||
      apiError.errorCode === 'LLM_INTERNAL_ERROR'
    )
      return 'PROVIDER_UNAVAILABLE'
    if (apiError.errorCode === 'VALIDATION_ERROR' || httpStatus === 400) return 'VALIDATION_ERROR'
    if (apiError.errorCode === 'UNAUTHORIZED' || httpStatus === 401) return 'UNAUTHORIZED'
    if (httpStatus === 403) return 'FORBIDDEN'
    if (httpStatus === 429) return 'RATE_LIMITED'
    if (apiError.errorCode === 'TIMEOUT') return 'TIMEOUT'
    if (apiError.errorCode === 'NETWORK_ERROR') return 'NETWORK_ERROR'
    if ((httpStatus && httpStatus >= 500) || apiError.errorCode === 'INTERNAL_ERROR') return 'SERVER_ERROR'
    return 'UNKNOWN'
  })()

  const message = friendlyMessage(category, apiError.message)
  return { category, message }
}

function friendlyMessage(category: AiErrorCategory, backendMessage: string): string {
  switch (category) {
    case 'NOT_CONFIGURED':
      return 'AI Assistant is not configured for this environment yet.'
    case 'NOT_READY':
      return 'AI services are currently being configured and are not available yet.'
    case 'PROVIDER_NOT_CONFIGURED':
      return 'The AI provider is not configured on the server. An operator needs to set a valid LLM API key.'
    case 'PROVIDER_UNAVAILABLE':
      return 'The AI provider is temporarily unavailable. Please try again shortly.'
    case 'UNAUTHORIZED':
      return 'Your dashboard session could not be authenticated. Re-enter your access token and try again.'
    case 'FORBIDDEN':
      return 'You do not have permission to use the AI Assistant.'
    case 'RATE_LIMITED':
      return 'Too many requests. Please wait a moment and try again.'
    case 'VALIDATION_ERROR':
      return backendMessage || 'That message could not be sent as written.'
    case 'TIMEOUT':
      return 'The request took too long to respond. Please try again.'
    case 'NETWORK_ERROR':
      return 'Could not reach the Control Center backend.'
    case 'SERVER_ERROR':
      return 'The server encountered an unexpected error. Please try again.'
    default:
      return backendMessage || 'An unexpected error occurred.'
  }
}
