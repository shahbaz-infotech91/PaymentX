package com.paymentx.controlcenter.exception;

/**
 * ENGLISH: Thrown by AiChatService whenever a real AI answer cannot be
 * produced - which, in Phase 3.1, is every single time, since no
 * Prompt/LLM/RAG/MCP Gateway/Agent Orchestrator service exists yet.
 * What it does: carries one of two real errorCodes - "AI_NOT_CONFIGURED"
 * when control-center.ai.enabled=false (an operator hasn't turned the
 * feature on at all), or "AI_SERVICE_NOT_READY" when enabled=true but
 * the real backing services this phase deliberately does not implement
 * are still missing. Why it exists: this phase's explicit "DO NOT
 * return fake AI content... the endpoint may return a proper
 * structured status such as AI_SERVICE_NOT_READY with an appropriate
 * HTTP status and message" requirement - a dedicated exception type
 * (rather than reusing generic ControlCenterException, which always
 * maps to 502) lets GlobalExceptionHandler map this to the
 * semantically correct 503 Service Unavailable ("not ready yet", not
 * "upstream call failed"). How it will communicate with the backend:
 * caught by GlobalExceptionHandler.handleAiServiceNotReady, turned
 * into a real ApiResponse.error(...) envelope the frontend's
 * useAiChat hook branches on by errorCode.
 *
 * HINGLISH: AiChatService jab bhi ek real AI answer produce nahi kar
 * sakta tab ise throw karta hai - jo, Phase 3.1 me, har baar hota hai,
 * kyunki abhi koi Prompt/LLM/RAG/MCP Gateway/Agent Orchestrator
 * service exist hi nahi karti. Ye kya karti hai: do real errorCodes me
 * se ek carry karti hai - "AI_NOT_CONFIGURED" jab
 * control-center.ai.enabled=false ho (ek operator ne feature ko on hi
 * nahi kiya), ya "AI_SERVICE_NOT_READY" jab enabled=true ho lekin real
 * backing services jo ye phase jaan-boojh kar implement nahi karta wo
 * abhi bhi missing hain. Ye dashboard me kyu hai: is phase ka explicit
 * "fake AI content return mat karo... endpoint ek proper structured
 * status jaise AI_SERVICE_NOT_READY ek appropriate HTTP status aur
 * message ke saath return kar sakta hai" requirement - ek dedicated
 * exception type (generic ControlCenterException reuse karne ke
 * bajaye, jo hamesha 502 par map hoti hai) GlobalExceptionHandler ko
 * ise semantically correct 503 Service Unavailable par map karne deta
 * hai ("abhi ready nahi hai", "upstream call fail hui" nahi). Backend
 * se kaise connect hogi: GlobalExceptionHandler.handleAiServiceNotReady
 * ise catch karta hai, ek real ApiResponse.error(...) envelope me
 * convert karta hai jise frontend ka useAiChat hook errorCode ke basis
 * par branch karta hai.
 */
public class AiServiceNotReadyException extends ControlCenterException {

    public AiServiceNotReadyException(String errorCode, String message) {
        super(errorCode, message);
    }
}
