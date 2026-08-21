package com.paymentx.controlcenter.dto.ai;

/**
 * ENGLISH: The real, honest status of one AI platform component, used
 * inside AiHealthResponse.components. What it does: gives the frontend
 * three distinct, truthful states instead of a single UP/DOWN boolean
 * that would misrepresent "this service doesn't exist in the codebase
 * at all" as merely "temporarily unreachable" - NOT_IMPLEMENTED (no
 * such module/service exists in this repository yet - Prompt/LLM/
 * Embedding/RAG/MCP Gateway/Agent Orchestrator, Phase 3.2-3.8),
 * NOT_READY (the module/contract exists - Phase 3.1's AI Chat
 * Interface controller/service - but has no real backing behind it
 * yet), READY (genuinely functional). Why it exists: this phase's
 * explicit "do not claim services are healthy when they don't exist"
 * rule, made impossible to violate by construction - there is no READY
 * value this phase's code ever assigns. How it will communicate with
 * the backend: serialized by Jackson as a plain string inside
 * AiHealthResponse.components' values.
 *
 * HINGLISH: Ek AI platform component ka real, honest status,
 * AiHealthResponse.components ke andar use hota hai. Ye kya karti hai:
 * frontend ko teen alag, truthful states deta hai ek single UP/DOWN
 * boolean ke bajaye jo "ye service repository me exist hi nahi karti"
 * ko sirf "temporarily unreachable" jaisa misrepresent kar deta -
 * NOT_IMPLEMENTED (is repository me abhi aisa koi module/service
 * exist hi nahi karta - Prompt/LLM/Embedding/RAG/MCP Gateway/Agent
 * Orchestrator, Phase 3.2-3.8), NOT_READY (module/contract exist karta
 * hai - Phase 3.1 ka AI Chat Interface controller/service - lekin
 * abhi iske peeche koi real backing nahi hai), READY (genuinely
 * functional). Ye dashboard me kyu hai: is phase ka explicit "jo
 * services exist hi nahi karti unhe healthy claim mat karo" rule, jise
 * construction se hi violate karna impossible bana diya gaya hai - is
 * phase ka koi code kabhi READY value assign nahi karta. Backend se
 * kaise connect hogi: Jackson isse AiHealthResponse.components ki
 * values ke andar ek plain string ke roop me serialize karta hai.
 */
public enum AiComponentStatus {
    NOT_IMPLEMENTED,
    NOT_READY,
    READY
}
