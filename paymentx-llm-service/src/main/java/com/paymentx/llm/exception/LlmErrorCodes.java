package com.paymentx.llm.exception;

/**
 * English:
 * LLM Service's own business error codes - deliberately NOT added to
 * paymentx-common-library's ErrorCodes (see that class's javadoc and
 * ADR 0004: shared, cross-cutting codes only; service-owned business
 * codes stay defined where they are thrown - matches Prompt Service's
 * PromptErrorCodes pattern exactly). A caller (AI Chat Service today,
 * Agent Orchestrator later) needs to distinguish "the provider is not
 * configured" from "the provider timed out" from "the provider rejected
 * our credentials" programmatically, not just from a human-readable
 * message - each code below maps to exactly one HTTP status and one
 * retryable flag in LlmException's static factories.
 * Why it exists: Step 13/36 of the Phase 3.3 brief - honest, specific
 * failure reporting instead of a single generic "AI failed" code.
 * How it communicates with other components: passed into LlmException
 * at every throw site in AnthropicLlmProvider/LlmServiceImpl; surfaced
 * verbatim as ErrorResponse.errorCode by GlobalExceptionHandler.
 *
 * Hinglish:
 * LLM Service ke apne business error codes - jaan-boojh kar
 * paymentx-common-library ke ErrorCodes me add NAHI kiye gaye (us
 * class ka javadoc aur ADR 0004 dekho: sirf shared, cross-cutting
 * codes; service-owned business codes wahin defined rehte hain jahan
 * throw hote hain - Prompt Service ke PromptErrorCodes pattern se
 * exactly match karta hai). Ek caller (aaj AI Chat Service, baad me
 * Agent Orchestrator) ko programmatically distinguish karna hota hai
 * ki "provider configured hi nahi hai" vs "provider timeout ho gaya"
 * vs "provider ne humare credentials reject kar diye" - sirf ek
 * human-readable message se nahi. Neeche har code exactly ek HTTP
 * status aur ek retryable flag par map hota hai LlmException ke static
 * factories me.
 * Ye kyu hai: Phase 3.3 brief ka Step 13/36 - honest, specific failure
 * reporting, ek generic "AI failed" code ke bajaye.
 * Dusre components se kaise communicate karta hai: AnthropicLlmProvider/
 * LlmServiceImpl me har throw site par LlmException me pass hota hai;
 * GlobalExceptionHandler dwara ErrorResponse.errorCode ke roop me
 * verbatim surface hota hai.
 */
public final class LlmErrorCodes {

    private LlmErrorCodes() {
    }

    public static final String LLM_NOT_CONFIGURED = "LLM_NOT_CONFIGURED";
    public static final String LLM_CREDENTIALS_REJECTED = "LLM_CREDENTIALS_REJECTED";
    public static final String LLM_PROVIDER_TIMEOUT = "LLM_PROVIDER_TIMEOUT";
    public static final String LLM_RATE_LIMITED = "LLM_RATE_LIMITED";
    public static final String LLM_PROVIDER_UNAVAILABLE = "LLM_PROVIDER_UNAVAILABLE";
    public static final String LLM_INVALID_REQUEST = "LLM_INVALID_REQUEST";
    public static final String LLM_RESPONSE_INVALID = "LLM_RESPONSE_INVALID";
    public static final String LLM_INTERNAL_ERROR = "LLM_INTERNAL_ERROR";
}
