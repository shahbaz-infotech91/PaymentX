/**
 * ENGLISH: Strongly typed models for the Phase 3.1 AI Assistant
 * feature. What it does: mirrors the Control Center backend's real
 * dto/ai/*.java records (AiChatRequest, AiChatResponse, AiRole,
 * AiHealthResponse, AiComponentStatus) plus frontend-only types for
 * client-side-only state (AiConversation/AiMessage - no server-side
 * persistence exists yet, see utils/aiConversationStore.ts) and error
 * classification (AiErrorCategory) the UI needs to render an honest,
 * specific message instead of one generic failure banner. Why it
 * exists: this phase's explicit "avoid any/unknown unless genuinely
 * required, avoid untyped API responses, use discriminated unions
 * where appropriate" requirement - AiMessage.status and AiErrorCategory
 * are both real discriminated unions a switch/if-chain can exhaustively
 * narrow over. How it will communicate with the backend:
 * AiChatRequest/AiChatResponse/AiHealthResponse/AiComponentStatus/AiRole
 * are the exact JSON shapes services/aiService.ts sends/receives from
 * POST /api/v1/ai/chat and GET /api/v1/ai/health.
 *
 * HINGLISH: Phase 3.1 AI Assistant feature ke liye strongly typed
 * models. Ye kya karti hai: Control Center backend ke real
 * dto/ai/*.java records (AiChatRequest, AiChatResponse, AiRole,
 * AiHealthResponse, AiComponentStatus) ko mirror karta hai plus
 * frontend-only types client-side-only state ke liye (AiConversation/
 * AiMessage - abhi koi server-side persistence exist nahi karti,
 * utils/aiConversationStore.ts dekho) aur error classification
 * (AiErrorCategory) jo UI ko ek honest, specific message render karne
 * ke liye chahiye, ek generic failure banner ke bajaye. Ye dashboard
 * me kyu hai: is phase ka explicit "any/unknown avoid karo jab tak
 * genuinely required na ho, untyped API responses avoid karo,
 * discriminated unions use karo jahan appropriate ho" requirement -
 * AiMessage.status aur AiErrorCategory dono real discriminated unions
 * hain jinhe ek switch/if-chain exhaustively narrow kar sakta hai.
 * Backend se kaise connect hogi: AiChatRequest/AiChatResponse/
 * AiHealthResponse/AiComponentStatus/AiRole exactly wahi JSON shapes
 * hain jo services/aiService.ts POST /api/v1/ai/chat aur GET
 * /api/v1/ai/health se bhejta/receive karta hai.
 */

/** Mirrors backend dto/ai/AiRole.java. */
export type AiRole = 'USER' | 'ASSISTANT' | 'SYSTEM'

/** Mirrors backend dto/ai/AiComponentStatus.java - see that file's javadoc for why READY is never actually sent in Phase 3.1. */
export type AiComponentStatus = 'NOT_IMPLEMENTED' | 'NOT_READY' | 'READY'

/** Mirrors backend dto/ai/AiChatRequest.java - the POST /api/v1/ai/chat request body. */
export interface AiChatRequest {
  conversationId: string | null
  message: string
}

/**
 * Mirrors backend dto/ai/AiChatResponse.java. Phase 3.1 declared this
 * shape before anything could ever populate it; Phase 3.3 first
 * populated it from a direct LLM Service call; Phase 3.6 rewired the
 * backend to RAG Service; Phase 3.8 rewired it again to Agent
 * Orchestrator (see AiChatService.java's javadoc), so `status` is now
 * one of Agent Orchestrator's own real, honest outcomes, passed through
 * verbatim (see AgentResponseStatus's javadoc in
 * paymentx-agent-orchestrator): "COMPLETED" (a real, grounded answer -
 * mapped from Agent Orchestrator's "SUCCESS"), "INSUFFICIENT_CONTEXT"
 * (PaymentX knowledge/data had nothing relevant enough), "REFUSED" (the
 * LLM itself declined to answer), "DENIED" (the request would have
 * required a financial write operation, which is never permitted),
 * "MAX_ITERATIONS" (the bounded agent loop ran out of steps before
 * reaching an answer), or "TIMEOUT" (the overall agent execution
 * deadline was hit).
 */
export interface AiChatResponse {
  conversationId: string
  messageId: string
  role: AiRole
  content: string
  timestamp: string
  status: string
}

/** Mirrors backend dto/ai/AiHealthResponse.java - the GET /api/v1/ai/health response body. */
export interface AiHealthResponse {
  status: string
  components: Record<string, AiComponentStatus>
  timestamp: string
}

/**
 * The honest, specific reason a chat send failed - classified by
 * classifyAiError() (services/aiService.ts) from the real ApiErrorResponse
 * axiosClient.ts already produces plus the real HTTP status. A discriminated
 * union so ChatMessage/ErrorState rendering can render distinct, useful copy
 * per category instead of one generic "something went wrong."
 */
export type AiErrorCategory =
  | 'NOT_CONFIGURED'
  | 'NOT_READY'
  | 'PROVIDER_NOT_CONFIGURED'
  | 'PROVIDER_UNAVAILABLE'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'RATE_LIMITED'
  | 'VALIDATION_ERROR'
  | 'TIMEOUT'
  | 'NETWORK_ERROR'
  | 'SERVER_ERROR'
  | 'UNKNOWN'

export interface AiClassifiedError {
  category: AiErrorCategory
  message: string
}

/** One chat bubble's real, client-side lifecycle - never fabricated content, only what was actually typed/received. */
export type AiMessageStatus = 'SENDING' | 'SENT' | 'ERROR'

export interface AiMessage {
  id: string
  role: AiRole
  content: string
  timestamp: string
  status: AiMessageStatus
  /** Set only when status is 'ERROR' - the real classified failure reason, never a fabricated assistant reply. */
  error?: AiClassifiedError
}

/**
 * A client-side-only conversation (see utils/aiConversationStore.ts javadoc
 * for why - no backend conversation persistence exists yet, Phase 3.1).
 * title is derived from the first real user message, never invented.
 */
export interface AiConversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messages: AiMessage[]
}
