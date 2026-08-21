package com.paymentx.vector.exception;

import com.paymentx.common.exception.PaymentXException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * English:
 * The single exception type every real failure in this service is
 * thrown as - matches LLM/Embedding Service's LlmException/
 * EmbeddingException `errorCode` + `httpStatus` shape, minus the
 * `retryable` distinction those two carry: unlike LLM/Embedding
 * Service, Vector Service never calls an external HTTP provider - every
 * failure here (not-found, dimension mismatch, invalid topK, malformed
 * request) is a deterministic application/database-level rejection that
 * would fail identically on every retry, so there is no Resilience4j
 * retry configuration in this service to feed a `retryable` flag into
 * (PaymentXException's constructor still requires the boolean - it is
 * always passed `false` here, honestly, rather than adding an unused
 * distinction).
 * Why it exists: Step 22's specific error codes (VECTOR_DIMENSION_MISMATCH
 * etc.) and this platform's established exception-handling convention.
 * How it communicates with other components: thrown by
 * VectorStoreServiceImpl at every validation/not-found site; caught by
 * GlobalExceptionHandler.
 *
 * Hinglish:
 * Is service me har real failure jis ek exception type se throw hoti
 * hai - LLM/Embedding Service ke LlmException/EmbeddingException
 * `errorCode` + `httpStatus` shape se match karta hai, un dono ke
 * `retryable` distinction ke bina: LLM/Embedding Service ke ulat,
 * Vector Service kabhi ek external HTTP provider call nahi karta - yahan
 * har failure (not-found, dimension mismatch, invalid topK, malformed
 * request) ek deterministic application/database-level rejection hai jo
 * har retry par identically fail hogi, isliye is service me koi
 * Resilience4j retry configuration nahi hai jise ek `retryable` flag
 * feed kiya jaaye (PaymentXException ka constructor ab bhi boolean
 * maangta hai - yahan hamesha `false` pass hota hai, honestly, ek
 * unused distinction add karne ke bajaye).
 * Ye kyu hai: Step 22 ke specific error codes (VECTOR_DIMENSION_MISMATCH
 * etc.) aur is platform ka established exception-handling convention.
 * Dusre components se kaise communicate karta hai: VectorStoreServiceImpl
 * har validation/not-found site par ise throw karta hai;
 * GlobalExceptionHandler ise catch karta hai.
 */
@Getter
public class VectorException extends PaymentXException {

    private final HttpStatus httpStatus;

    public VectorException(String errorCode, String message, HttpStatus httpStatus) {
        super(errorCode, message, false);
        this.httpStatus = httpStatus;
    }

    public static VectorException documentNotFound(String message) {
        return new VectorException(VectorErrorCodes.DOCUMENT_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    public static VectorException chunkNotFound(String message) {
        return new VectorException(VectorErrorCodes.CHUNK_NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    public static VectorException dimensionMismatch(String message) {
        return new VectorException(VectorErrorCodes.VECTOR_DIMENSION_MISMATCH, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static VectorException invalidVector(String message) {
        return new VectorException(VectorErrorCodes.VECTOR_INVALID_VALUE, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static VectorException invalidTopK(String message) {
        return new VectorException(VectorErrorCodes.INVALID_TOP_K, message, HttpStatus.BAD_REQUEST);
    }

    public static VectorException invalidRequest(String message) {
        return new VectorException(VectorErrorCodes.INVALID_REQUEST, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
