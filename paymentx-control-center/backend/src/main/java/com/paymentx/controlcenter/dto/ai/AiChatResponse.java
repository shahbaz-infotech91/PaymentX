package com.paymentx.controlcenter.dto.ai;

import java.time.OffsetDateTime;

/**
 * ENGLISH: The declared future success shape of POST /api/v1/ai/chat -
 * defined now (Phase 3.1) so the contract is stable before LLM Service
 * (Phase 3.3) exists to ever actually populate it, per this phase's
 * explicit "create the minimum backend contract required for the
 * future AI Chat Interface" requirement. IMPORTANT - current real
 * behavior: no code path in this module returns this type today.
 * AiChatService.sendMessage(...) always throws
 * AiServiceNotReadyException (see that class), which
 * GlobalExceptionHandler turns into a real 503 ApiResponse.error(...)
 * envelope instead - there is no LLM/Prompt/RAG/Agent Orchestrator
 * behind this backend yet, so content would otherwise have to be
 * fabricated, which this phase explicitly forbids. This record exists
 * purely as the forward-declared response shape a future
 * AiChatService can construct once a real answer exists. Why it
 * exists: keeps AiController's method signature
 * (ApiResponse&lt;AiChatResponse&gt;) honest about what a *successful*
 * chat call will eventually return, without requiring a second
 * breaking contract change later. How it will communicate with the
 * backend: once Phase 3.2+ exists, AiChatService will construct this
 * from a real LLM Service response instead of throwing.
 *
 * HINGLISH: POST /api/v1/ai/chat ka declared future success shape -
 * abhi (Phase 3.1) hi define kiya gaya hai taaki contract stable ho
 * LLM Service (Phase 3.3) exist karne se pehle jo ise actually
 * populate karega, is phase ki explicit "future AI Chat Interface ke
 * liye minimum backend contract banao" requirement ke hisaab se.
 * IMPORTANT - current real behavior: is module me koi code path aaj
 * ye type return nahi karta. AiChatService.sendMessage(...) hamesha
 * AiServiceNotReadyException throw karta hai (wo class dekho), jise
 * GlobalExceptionHandler ek real 503 ApiResponse.error(...) envelope
 * me convert karta hai - abhi is backend ke peeche koi LLM/Prompt/RAG/
 * Agent Orchestrator nahi hai, isliye content ko warna fabricate karna
 * padta, jo is phase explicitly mana karta hai. Ye record sirf ek
 * forward-declared response shape ke roop me exist karta hai jise ek
 * future AiChatService construct kar sakega jab ek real answer exist
 * kare. Ye dashboard me kyu hai: AiController ke method signature
 * (ApiResponse&lt;AiChatResponse&gt;) ko honest rakhta hai is baare me
 * ki ek *successful* chat call eventually kya return karegi, bina baad
 * me ek doosra breaking contract change kiye. Backend se kaise connect
 * hogi: ek baar Phase 3.2+ exist karne ke baad, AiChatService throw
 * karne ke bajaye ise ek real LLM Service response se construct
 * karega.
 */
public record AiChatResponse(
        String conversationId,
        String messageId,
        AiRole role,
        String content,
        OffsetDateTime timestamp,
        String status
) {
}
