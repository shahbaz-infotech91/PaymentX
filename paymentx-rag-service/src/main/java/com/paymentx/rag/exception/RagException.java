package com.paymentx.rag.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real RAG orchestration failure is
 * thrown as - matches LLM/Embedding/Vector Service's XxxException
 * `errorCode` + `httpStatus` (+ `retryable`) shape exactly. `retryable`
 * matters here more than it did for Vector Service: RAG Service DOES
 * call four real external HTTP dependencies (Embedding/Vector/Prompt/
 * LLM Service), each behind its own Resilience4j retry instance (see
 * ResilienceConfig), and each instance's RetryConfigCustomizer reads
 * this exact flag - a downstream 503/timeout is retryable=true, a
 * downstream 4xx (bad request, auth) is retryable=false, matching Step
 * 30's "retry only transient failures... do NOT retry invalid request/
 * authorization failure/configuration errors."
 * Why it exists: Step 28's exact required error-code list, thrown
 * consistently from every client class and RagServiceImpl.
 * How it communicates with other components: thrown by
 * EmbeddingServiceClient/VectorServiceClient/PromptServiceClient/
 * LlmServiceClient (mapped from each downstream service's own real
 * errorCode/HTTP status) and by RagServiceImpl (INVALID_QUERY,
 * CONTEXT_TOO_LARGE); caught by GlobalExceptionHandler.
 *
 * Hinglish:
 * Har real RAG orchestration failure jis ek exception type se throw
 * hoti hai - LLM/Embedding/Vector Service ke XxxException `errorCode` +
 * `httpStatus` (+ `retryable`) shape se exactly match karta hai.
 * `retryable` yahan Vector Service se zyada matter karta hai: RAG
 * Service genuinely chaar real external HTTP dependencies call karti
 * hai (Embedding/Vector/Prompt/LLM Service), har ek apne Resilience4j
 * retry instance ke peeche (ResilienceConfig dekho), aur har instance
 * ka RetryConfigCustomizer exactly yahi flag padhta hai - ek downstream
 * 503/timeout retryable=true hai, ek downstream 4xx (bad request, auth)
 * retryable=false hai, Step 30 se match karte hue - "sirf transient
 * failures retry karo... invalid request/authorization failure/
 * configuration errors par retry MAT karo."
 * Ye kyu hai: Step 28 ki exact required error-code list, har client
 * class aur RagServiceImpl se consistently throw ki gayi.
 * Dusre components se kaise communicate karta hai:
 * EmbeddingServiceClient/VectorServiceClient/PromptServiceClient/
 * LlmServiceClient (har downstream service ke apne real errorCode/HTTP
 * status se map kiya gaya) aur RagServiceImpl (INVALID_QUERY,
 * CONTEXT_TOO_LARGE) ise throw karte hain; GlobalExceptionHandler ise
 * catch karta hai.
 */
@Getter
public class RagException extends PaymentXException {

    private final HttpStatus httpStatus;

    public RagException(String errorCode, String message, boolean retryable, HttpStatus httpStatus) {
        super(errorCode, message, retryable);
        this.httpStatus = httpStatus;
    }

    public static RagException invalidQuery(String message) {
        return new RagException(RagErrorCodes.INVALID_QUERY, message, false, HttpStatus.BAD_REQUEST);
    }

    public static RagException embeddingServiceUnavailable(String message, boolean retryable) {
        return new RagException(RagErrorCodes.EMBEDDING_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static RagException vectorServiceUnavailable(String message, boolean retryable) {
        return new RagException(RagErrorCodes.VECTOR_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static RagException promptServiceUnavailable(String message, boolean retryable) {
        return new RagException(RagErrorCodes.PROMPT_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static RagException llmServiceUnavailable(String message, boolean retryable) {
        return new RagException(RagErrorCodes.LLM_SERVICE_UNAVAILABLE, message, retryable, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static RagException llmTimeout(String message) {
        return new RagException(RagErrorCodes.LLM_TIMEOUT, message, true, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static RagException contextTooLarge(String message) {
        return new RagException(RagErrorCodes.CONTEXT_TOO_LARGE, message, false, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static RagException internalError(String message) {
        return new RagException(RagErrorCodes.INTERNAL_ERROR, message, false, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
