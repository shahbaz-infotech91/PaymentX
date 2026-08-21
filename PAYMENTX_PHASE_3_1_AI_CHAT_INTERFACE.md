# PaymentX — Phase 3.1: AI Chat Interface

**Status:** Implemented — UI + backend contract only. No LLM, Prompt Service, RAG Service, MCP Gateway, or Agent Orchestrator exists yet (Phase 3.2–3.8). Every section below marks **CURRENT** vs **FUTURE** explicitly.

---

## 1. Architecture

**CURRENT:**

```
Browser (React)
   │  Control Center Frontend — new /ai-assistant page, reused shell (AppLayout/Header/Sidebar/Footer/theme)
   ▼
Control Center Backend (:8089) — new AiController (/api/v1/ai/chat, /api/v1/ai/health)
   │  AiChatService — always throws AiServiceNotReadyException (no real backend to call)
   ▼
(nothing — no AI Chat Interface microservice, no API Gateway route, no LLM exists yet)
```

**FUTURE** (per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §5): a dedicated `paymentx-ai-chat-interface` service reached through a new API Gateway route, with `AiChatService`'s current body replaced by a real call to it — the controller/DTO contract defined now stays stable through that change.

No new React app, no new Maven module, and no new Docker/gateway wiring were introduced in this phase — deliberately, per this phase's own scope (UI + a minimal backend contract only).

## 2. Frontend Flow

**CURRENT:** `pages/AiAssistantPage.tsx` → `hooks/useAiChat.ts` (owns conversation state, sessionStorage-backed — see §8) + `hooks/useAiHealth.ts` (polls health every 30s) → `services/aiService.ts` (`sendAiChatMessage`, `fetchAiHealth`, `classifyAiError`) → `api/axiosClient.ts` (existing, reused as-is — auth header, timeout, error normalization) → real HTTP to the backend.

Components: `ChatWindow` (message list + auto-scroll + composer), `ChatHeader` (title + `AIStatus` + clear action), `ChatMessage` (per-bubble state: SENDING/SENT/ERROR, retry, copy), `ChatInput` (Enter/Shift+Enter, char limit, disabled-while-sending), `ConversationList` (real, client-stored conversations), `AIStatus` (real health indicator).

## 3. Backend Flow

**CURRENT:** `AiController` (`/api/v1/ai/chat`, `/api/v1/ai/health`) → `AiChatService` → either `AiServiceNotReadyException("AI_NOT_CONFIGURED", …)` (when `control-center.ai.enabled=false`, the default) or `AiServiceNotReadyException("AI_SERVICE_NOT_READY", …)` (when enabled but unbuilt) → `GlobalExceptionHandler.handleAiServiceNotReady` → HTTP 503 with the standard `ApiResponse`/`ErrorResponse` envelope. `health()` never throws — it returns a real, honest per-component breakdown (see §7).

**FUTURE:** `AiChatService.sendMessage` is replaced with a real call to the AI Chat Interface/Agent Orchestrator service; `AiChatResponse` (already defined, unused today) starts being populated instead of the exception being thrown.

## 4. API Contract

Following existing convention (`/api/v1/{resource}` + `ApiResponse<T>`/`ErrorResponse`/`PageResponse<T>` envelope), per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §17 — not the `/api/ai/...` shape sketched in the Phase 3.1 brief, since it doesn't match this backend's real routes.

**`POST /api/v1/ai/chat`**
```json
// Request
{ "conversationId": "uuid-or-null", "message": "Why did PMT-123 fail?" }

// CURRENT response (always, in Phase 3.1) - HTTP 503
{ "success": false, "data": null,
  "error": { "errorCode": "AI_NOT_CONFIGURED | AI_SERVICE_NOT_READY", "message": "...", "path": "/api/v1/ai/chat" },
  "timestamp": "..." }

// FUTURE response shape (defined now in AiChatResponse.java/types/ai.ts, not returned by any code path yet) - HTTP 200
{ "success": true,
  "data": { "conversationId": "...", "messageId": "...", "role": "ASSISTANT", "content": "...", "timestamp": "...", "status": "..." },
  "error": null, "timestamp": "..." }
```

**`GET /api/v1/ai/health`** — HTTP 200 always (a health check succeeding is distinct from what it reports):
```json
{ "success": true,
  "data": { "status": "NOT_CONFIGURED | NOT_READY",
    "components": {
      "chatInterface": "NOT_READY",
      "promptService": "NOT_IMPLEMENTED", "llmService": "NOT_IMPLEMENTED", "embeddingService": "NOT_IMPLEMENTED",
      "ragService": "NOT_IMPLEMENTED", "mcpGateway": "NOT_IMPLEMENTED", "agentOrchestrator": "NOT_IMPLEMENTED"
    }, "timestamp": "..." },
  "error": null, "timestamp": "..." }
```
`READY` is a real, valid `AiComponentStatus` value but is never assigned by any Phase 3.1 code path.

Validation: `message` is `@NotBlank`, 1–4000 chars (`AiChatRequest.java`) — violation → HTTP 400 `VALIDATION_ERROR` via the existing `GlobalExceptionHandler`.

## 5. Authentication

**CURRENT:** No new authentication mechanism. `/api/v1/ai/chat` inherits the existing `DashboardAuthFilter` gate automatically (any `/api/**` path, when `control-center.security.enabled=true`, default false). `/api/v1/ai/health` was explicitly added to `DashboardAuthFilter`'s exemption set (alongside `/api/v1/health`) so the status indicator is reachable before a user has entered a dashboard token — same reasoning as the existing health exemption. No JWT, no new token type, no bypass of the API Gateway (there is no gateway path involved yet — see §1).

## 6. Error Handling

The frontend's `classifyAiError` (`services/aiService.ts`) turns any failure into one of: `NOT_CONFIGURED`, `NOT_READY`, `UNAUTHORIZED`, `FORBIDDEN`, `RATE_LIMITED`, `VALIDATION_ERROR`, `TIMEOUT`, `NETWORK_ERROR`, `SERVER_ERROR`, `UNKNOWN` — each with an enterprise-friendly message, never a raw stack trace or Axios internal string. `ChatMessage` renders the classified error inline on the failed bubble with a **Retry** action (`useAiChat.ts`'s `retryMessage`, re-sends the specific prior user message).

## 7. Health Status

Real, backend-reported, three-state per component (`NOT_IMPLEMENTED` / `NOT_READY` / `READY` — `AiComponentStatus.java`/`types/ai.ts`) — never a single fabricated boolean. `AIStatus.tsx` renders exactly what `GET /api/v1/ai/health` returns: loading → "Connecting…", unreachable → "AI service is not configured yet", `NOT_CONFIGURED` → "Not Configured", `NOT_READY` → "AI Service Unavailable". No code path renders "Ready" today.

## 8. State Management

Per Step 11's explicit guidance: React Query for the two real server calls (`useAiHealth`, and the `sendAiChatMessage` mutation inside `useAiChat`); plain `useState` + `sessionStorage` (`utils/aiConversationStore.ts`, mirroring `utils/dashboardAuth.ts`'s existing pattern) for conversation/message history, since **no server-side conversation persistence exists yet** — the backend has no `ai_conversations`/`ai_messages` table (see `PAYMENTX_PHASE_3_ARCHITECTURE.md` §6 for the eventual schema). No Redux was introduced — the existing Redux slice remains theme-only, unchanged.

## 9. Responsive Behavior

Desktop (`md+`): permanent two-pane layout (280px `ConversationList` + flexible `ChatWindow`) inside a bordered `Paper`. Mobile/tablet: `ConversationList` pane hidden; a "Open conversations" icon button in `ChatHeader` opens it in a temporary MUI `Drawer` (mirroring `Sidebar.tsx`'s existing desktop/mobile Drawer split). Verified via `npm run build` (production bundle) and the automated test suite (§10) — a live-browser pass was not run in this session (see Known Limitations in the final report).

## 10. Testing

**Backend** (`mvn test`, plain JUnit5/Mockito/AssertJ — this module's existing convention, no Spring context): `AiChatServiceTest` (6 tests — both errorCode branches, health() component/status correctness), `AiControllerTest` (3 tests — delegation, exception propagation), `GlobalExceptionHandlerAiTest` (1 test — 503 mapping), `DashboardAuthFilterTest` (+1 test — `/api/v1/ai/health` exemption). **18/18 pass.**

**Frontend** (new: this repo had zero test infrastructure before Phase 3.1 — Vitest + Testing Library added, since Vite already builds this project): `aiService.test.ts` (8 — error classification), `AIStatus.test.tsx` (4 — every real status state), `ChatInput.test.tsx` (5 — Enter/Shift+Enter/disabled/enabled), `router.test.ts` (3 — routing wiring, drift guard), `AiAssistantPage.test.tsx` (5 — empty state, real classified error on send, new-conversation, pending state). **25/25 pass.**

## 11. Future Integration with Prompt Service (Phase 3.2)

`AiChatService.sendMessage` will call Prompt Service to resolve the system prompt for a conversation before forwarding to LLM Service — no code here assumes a specific prompt format today.

## 12. Future Integration with LLM Service (Phase 3.3)

`AiChatResponse.content` will be populated from a real LLM Service call; `AiChatService` starts constructing `AiChatResponse` instead of throwing, and the `AI_SERVICE_NOT_READY` branch narrows (LLM-specific failures get their own errorCode at that point, per `PAYMENTX_PHASE_3_ARCHITECTURE.md` §8).

## 13. Future Integration with Agent Orchestrator (Phase 3.8)

For tool-using requests (e.g. "investigate PMT-123"), `AiChatService` will delegate to Agent Orchestrator rather than LLM Service directly; `useAiChat.ts`'s conversation/message model does not need to change — an agent run's final answer is still just an `ASSISTANT`-role `AiChatResponse` from the frontend's perspective.

---

**No AI logic was faked to reach this state.** Every response the running system produces today is either real user input echoed back, or a real, honest `AI_NOT_CONFIGURED`/`AI_SERVICE_NOT_READY` failure.
