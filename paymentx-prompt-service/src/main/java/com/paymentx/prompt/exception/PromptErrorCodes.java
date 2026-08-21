package com.paymentx.prompt.exception;

/**
 * English:
 * Prompt Service's own business error codes - deliberately NOT added to
 * paymentx-common-library's ErrorCodes (see that class's javadoc and
 * ADR 0004: shared, cross-cutting codes only, service-owned business
 * codes stay defined where they are thrown, matching Validation
 * Service's/Payment Service's existing local error-code pattern).
 * Why it exists: Step 19 of the Phase 3.2 brief requires these exact
 * distinct codes rather than reusing generic RESOURCE_NOT_FOUND/
 * VALIDATION_ERROR/CONFLICT for every failure - a caller (AI Chat
 * Service today, LLM Service/Agent Orchestrator later) needs to
 * distinguish "this prompt key doesn't exist" from "this version
 * doesn't exist" from "you forgot a required variable" programmatically,
 * not just from a human-readable message.
 * How it communicates with other components: passed into
 * PromptNotFoundException / common's ConflictException / common's
 * PaymentXException at the point each is thrown in PromptServiceImpl;
 * surfaced verbatim as ErrorResponse.errorCode by GlobalExceptionHandler.
 *
 * Hinglish:
 * Prompt Service ke apne business error codes - jaan-boojh kar
 * paymentx-common-library ke ErrorCodes me add NAHI kiye gaye (us
 * class ka javadoc aur ADR 0004 dekho: sirf shared, cross-cutting
 * codes, service-owned business codes wahin defined rehte hain jahan
 * throw hote hain, Validation Service/Payment Service ke existing
 * local error-code pattern se match karte hue).
 * Ye kyu hai: Phase 3.2 brief ka Step 19 in exact distinct codes ko
 * mandatory karta hai, har failure ke liye generic RESOURCE_NOT_FOUND/
 * VALIDATION_ERROR/CONFLICT reuse karne ke bajaye - ek caller (aaj AI
 * Chat Service, baad me LLM Service/Agent Orchestrator) ko
 * programmatically distinguish karna hota hai ki "ye prompt key exist
 * nahi karti" vs "ye version exist nahi karta" vs "ek required
 * variable bhool gaye" - sirf ek human-readable message se nahi.
 * Dusre components se kaise communicate karta hai: PromptServiceImpl
 * me jahan har ek throw hota hai wahan PromptNotFoundException /
 * common ke ConflictException / common ke PaymentXException me pass
 * kiya jaata hai; GlobalExceptionHandler dwara ErrorResponse.errorCode
 * ke roop me verbatim surface hota hai.
 */
public final class PromptErrorCodes {

    private PromptErrorCodes() {
    }

    public static final String PROMPT_NOT_FOUND = "PROMPT_NOT_FOUND";
    public static final String PROMPT_VERSION_NOT_FOUND = "PROMPT_VERSION_NOT_FOUND";
    public static final String NO_ACTIVE_VERSION = "NO_ACTIVE_VERSION";
    public static final String PROMPT_ALREADY_EXISTS = "PROMPT_ALREADY_EXISTS";
    public static final String PROMPT_VERSION_ALREADY_EXISTS = "PROMPT_VERSION_ALREADY_EXISTS";
    public static final String INVALID_PROMPT_STATUS = "INVALID_PROMPT_STATUS";
    public static final String ACTIVE_VERSION_CONFLICT = "ACTIVE_VERSION_CONFLICT";
    public static final String MISSING_VARIABLE = "MISSING_VARIABLE";
    public static final String UNKNOWN_VARIABLE = "UNKNOWN_VARIABLE";
    public static final String INVALID_PROMPT_CONTENT = "INVALID_PROMPT_CONTENT";
}
