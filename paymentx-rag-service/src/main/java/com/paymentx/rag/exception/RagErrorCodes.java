package com.paymentx.rag.exception;

/**
 * English:
 * RAG Service's own business error codes - deliberately NOT added to
 * paymentx-common-library's ErrorCodes (see that class's javadoc and
 * ADR 0004: shared, cross-cutting codes only; service-owned business
 * codes stay defined where they are thrown - matches every other AI
 * Platform service's XxxErrorCodes pattern exactly). Step 28's exact
 * required code list. NOTE - `INSUFFICIENT_CONTEXT` is deliberately
 * NOT one of these thrown-exception codes: Step 22 requires it be
 * returned as a truthful, normal RagQueryResponse.status value (HTTP
 * 200), never an exception/error - see RagQueryStatus's javadoc for why
 * treating "no relevant knowledge" as an HTTP error would be less
 * honest, not more, matching the exact reasoning LLM Service's
 * `refused` field and Embedding Service's health semantics already
 * established for their own "this is a real, non-error outcome" cases.
 * Why it exists: honest, specific failure reporting instead of a
 * generic "RAG failed" code, for the failures that ARE real service
 * failures (a downstream dependency being unreachable, misconfigured,
 * or timing out).
 * How it communicates with other components: passed into RagException
 * at every throw site in RagServiceImpl/the four client classes;
 * surfaced verbatim as ErrorResponse.errorCode by GlobalExceptionHandler.
 *
 * Hinglish:
 * RAG Service ke apne business error codes - jaan-boojh kar
 * paymentx-common-library ke ErrorCodes me add NAHI kiye gaye (us
 * class ka javadoc aur ADR 0004 dekho: sirf shared, cross-cutting
 * codes; service-owned business codes wahin defined rehte hain jahan
 * throw hote hain - har doosri AI Platform service ke XxxErrorCodes
 * pattern se exactly match karta hai). Step 28 ki exact required code
 * list. NOTE - `INSUFFICIENT_CONTEXT` jaan-boojh kar in thrown-exception
 * codes me se EK NAHI hai: Step 22 ko chahiye ki ye ek truthful, normal
 * RagQueryResponse.status value ke roop me return ho (HTTP 200), kabhi
 * ek exception/error nahi - RagQueryStatus ka javadoc dekho ki "no
 * relevant knowledge" ko ek HTTP error treat karna kam honest kyun
 * hoga, zyada nahi, LLM Service ke `refused` field aur Embedding
 * Service ke health semantics ke exact reasoning se match karte hue jo
 * unhone apne "ye ek real, non-error outcome hai" cases ke liye already
 * establish kiya tha.
 * Ye kyu hai: honest, specific failure reporting, ek generic "RAG
 * failed" code ke bajaye, un failures ke liye jo genuinely real service
 * failures hain (ek downstream dependency unreachable, misconfigured,
 * ya timing out ho rahi hai).
 * Dusre components se kaise communicate karta hai: RagServiceImpl/char
 * client classes me har throw site par RagException me pass hota hai;
 * GlobalExceptionHandler dwara ErrorResponse.errorCode ke roop me
 * verbatim surface hota hai.
 */
public final class RagErrorCodes {

    private RagErrorCodes() {
    }

    public static final String INVALID_QUERY = "INVALID_QUERY";
    public static final String EMBEDDING_SERVICE_UNAVAILABLE = "EMBEDDING_SERVICE_UNAVAILABLE";
    public static final String VECTOR_SERVICE_UNAVAILABLE = "VECTOR_SERVICE_UNAVAILABLE";
    public static final String PROMPT_SERVICE_UNAVAILABLE = "PROMPT_SERVICE_UNAVAILABLE";
    public static final String LLM_SERVICE_UNAVAILABLE = "LLM_SERVICE_UNAVAILABLE";
    public static final String LLM_TIMEOUT = "LLM_TIMEOUT";
    public static final String CONTEXT_TOO_LARGE = "CONTEXT_TOO_LARGE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
