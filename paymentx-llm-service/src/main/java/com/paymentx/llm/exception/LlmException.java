package com.paymentx.llm.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real LLM-call failure in this
 * service is thrown as - one class, not a two-class transient/
 * permanent hierarchy, because PaymentXException already carries a
 * `retryable` boolean (see its javadoc). Resilience4j's YAML
 * retry-exceptions/ignore-exceptions lists are class-based and cannot
 * express "retry this exception sometimes, not others" for one type -
 * config/ResilienceConfig.java's RetryConfigCustomizer bean supplies a
 * `retryOnException` predicate that reads this exact `retryable` flag
 * instead (see application.yml's
 * resilience4j.retry.instances.llmProvider for the rest of the tuning).
 * `httpStatus` is this class's own addition (PaymentXException doesn't
 * carry one) so GlobalExceptionHandler has one generic handler instead
 * of six near-identical ones, matching the reasoning Prompt Service's
 * PromptNotFoundException javadoc gives for not building a class
 * hierarchy where a field already carries the distinction.
 * Why it exists: Step 13 (never retry auth/invalid-request/content-
 * policy failures) and Step 36 (truthful, specific errors - never a
 * fabricated answer) of the Phase 3.3 brief.
 * How it communicates with other components: thrown by
 * AnthropicLlmProvider (mapped from typed com.anthropic.errors.*
 * exceptions) and LlmServiceImpl (LLM_NOT_CONFIGURED, before any
 * provider call is attempted); caught by GlobalExceptionHandler.
 *
 * Hinglish:
 * Is service me har real LLM-call failure jis ek exception type se
 * throw hoti hai - ek class, do-class transient/permanent hierarchy
 * nahi, kyunki PaymentXException already ek `retryable` boolean carry
 * karta hai (uska javadoc dekho). Resilience4j ki YAML retry-exceptions/
 * ignore-exceptions lists class-based hain aur ek hi type ke liye
 * "kabhi retry karo, kabhi mat karo" express nahi kar sakti -
 * config/ResilienceConfig.java ka RetryConfigCustomizer bean ek
 * `retryOnException` predicate deta hai jo exactly yahi `retryable`
 * flag padhta hai (baki tuning ke liye application.yml ka
 * resilience4j.retry.instances.llmProvider dekho). `httpStatus` is
 * class ka apna addition hai (PaymentXException koi nahi carry karta)
 * taaki GlobalExceptionHandler ke paas ek generic handler ho, chhe
 * near-identical handlers ke bajaye - wahi reasoning jo Prompt Service
 * ka PromptNotFoundException javadoc deta hai ki jab ek field already
 * distinction carry karta ho toh class hierarchy kyun nahi banani
 * chahiye.
 * Ye kyu hai: Phase 3.3 brief ka Step 13 (auth/invalid-request/content-
 * policy failures par kabhi retry mat karo) aur Step 36 (truthful,
 * specific errors - kabhi ek fabricated answer nahi).
 * Dusre components se kaise communicate karta hai:
 * AnthropicLlmProvider (typed com.anthropic.errors.* exceptions se map
 * kiya gaya) aur LlmServiceImpl (LLM_NOT_CONFIGURED, kisi bhi provider
 * call attempt se pehle) ise throw karte hain; GlobalExceptionHandler
 * ise catch karta hai.
 */
@Getter
public class LlmException extends PaymentXException {

    private final HttpStatus httpStatus;

    public LlmException(String errorCode, String message, boolean retryable, HttpStatus httpStatus) {
        super(errorCode, message, retryable);
        this.httpStatus = httpStatus;
    }

    public static LlmException notConfigured(String message) {
        return new LlmException(LlmErrorCodes.LLM_NOT_CONFIGURED, message, false, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static LlmException credentialsRejected(String message) {
        return new LlmException(LlmErrorCodes.LLM_CREDENTIALS_REJECTED, message, false, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static LlmException timeout(String message) {
        return new LlmException(LlmErrorCodes.LLM_PROVIDER_TIMEOUT, message, true, HttpStatus.GATEWAY_TIMEOUT);
    }

    public static LlmException rateLimited(String message) {
        return new LlmException(LlmErrorCodes.LLM_RATE_LIMITED, message, true, HttpStatus.TOO_MANY_REQUESTS);
    }

    public static LlmException providerUnavailable(String message) {
        return new LlmException(LlmErrorCodes.LLM_PROVIDER_UNAVAILABLE, message, true, HttpStatus.BAD_GATEWAY);
    }

    public static LlmException invalidRequest(String message) {
        return new LlmException(LlmErrorCodes.LLM_INVALID_REQUEST, message, false, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static LlmException responseInvalid(String message) {
        return new LlmException(LlmErrorCodes.LLM_RESPONSE_INVALID, message, false, HttpStatus.BAD_GATEWAY);
    }

    public static LlmException internalError(String message) {
        return new LlmException(LlmErrorCodes.LLM_INTERNAL_ERROR, message, false, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
