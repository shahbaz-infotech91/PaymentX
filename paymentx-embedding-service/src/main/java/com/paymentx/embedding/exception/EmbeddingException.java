package com.paymentx.embedding.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real embedding-call failure in this
 * service is thrown as - matches LLM Service's LlmException exactly
 * (see that class's javadoc for the full "one class with a retryable
 * flag, not a two-class hierarchy" rationale, which applies identically
 * here). `httpStatus` is this class's own addition; `retryable` comes
 * from PaymentXException and is read by ResilienceConfig's
 * RetryConfigCustomizer predicate, not by YAML class-based filtering.
 * Why it exists: Step 14 (never retry auth/invalid-request/dimension-
 * mismatch failures) and Step 7 (truthful, specific errors - never a
 * fabricated vector) of the Phase 3.4 brief.
 * How it communicates with other components: thrown by
 * OpenAiEmbeddingProvider (mapped from real HTTP status codes) and
 * EmbeddingServiceImpl (EMBEDDING_NOT_CONFIGURED, before any provider
 * call is attempted); caught by GlobalExceptionHandler.
 *
 * Hinglish:
 * Is service me har real embedding-call failure jis ek exception type
 * se throw hoti hai - LLM Service ke LlmException se exactly match
 * karta hai (us class ka javadoc dekho poore "ek class ek retryable
 * flag ke saath, do-class hierarchy nahi" rationale ke liye, jo yahan
 * bhi identically apply hota hai). `httpStatus` is class ka apna
 * addition hai; `retryable` PaymentXException se aata hai aur
 * ResilienceConfig ke RetryConfigCustomizer predicate dwara padha jaata
 * hai, YAML class-based filtering dwara nahi.
 * Ye kyu hai: Phase 3.4 brief ka Step 14 (auth/invalid-request/
 * dimension-mismatch failures par kabhi retry mat karo) aur Step 7
 * (truthful, specific errors - kabhi ek fabricated vector nahi).
 * Dusre components se kaise communicate karta hai:
 * OpenAiEmbeddingProvider (real HTTP status codes se map kiya gaya) aur
 * EmbeddingServiceImpl (EMBEDDING_NOT_CONFIGURED, kisi bhi provider call
 * attempt se pehle) ise throw karte hain; GlobalExceptionHandler ise
 * catch karta hai.
 */
@Getter
public class EmbeddingException extends PaymentXException {

    private final HttpStatus httpStatus;

    public EmbeddingException(String errorCode, String message, boolean retryable, HttpStatus httpStatus) {
        super(errorCode, message, retryable);
        this.httpStatus = httpStatus;
    }

    public static EmbeddingException notConfigured(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_NOT_CONFIGURED, message, false, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static EmbeddingException credentialsRejected(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_CREDENTIALS_REJECTED, message, false, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static EmbeddingException timeout(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_PROVIDER_TIMEOUT, message, true, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static EmbeddingException rateLimited(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_RATE_LIMITED, message, true, HttpStatus.TOO_MANY_REQUESTS);
    }

    public static EmbeddingException providerUnavailable(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_PROVIDER_UNAVAILABLE, message, true, HttpStatus.BAD_GATEWAY);
    }

    public static EmbeddingException invalidRequest(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_INVALID_REQUEST, message, false, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static EmbeddingException responseInvalid(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_RESPONSE_INVALID, message, false, HttpStatus.BAD_GATEWAY);
    }

    public static EmbeddingException dimensionMismatch(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_DIMENSION_MISMATCH, message, false, HttpStatus.BAD_GATEWAY);
    }

    public static EmbeddingException internalError(String message) {
        return new EmbeddingException(EmbeddingErrorCodes.EMBEDDING_INTERNAL_ERROR, message, false, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
