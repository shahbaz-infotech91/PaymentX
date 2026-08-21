package com.paymentx.embedding.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.PaymentXException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * English:
 * Matches LLM Service's/Prompt Service's GlobalExceptionHandler pattern
 * exactly - one place converting every exception thrown anywhere in
 * this service into the same ApiResponse&lt;Void&gt; error shape, never
 * a raw Spring stack trace and never a fabricated success body.
 * handleEmbeddingException reads ex.getHttpStatus() directly -
 * EmbeddingException already carries the correct status per failure
 * kind (see its javadoc), so one handler method covers all nine
 * EmbeddingErrorCodes.
 * Why it exists: this platform's established exception-handling
 * convention, applied to Phase 3.4.
 * How it communicates with other components: this IS backend-side code
 * every EmbeddingController method implicitly relies on instead of its
 * own try/catch blocks.
 *
 * Hinglish:
 * LLM Service/Prompt Service ke GlobalExceptionHandler pattern se
 * exactly match karta hai - ek jagah jo is service me kahin bhi throw
 * hui har exception ko usi ApiResponse&lt;Void&gt; error shape me
 * convert karti hai, kabhi ek raw Spring stack trace nahi aur kabhi ek
 * fabricated success body nahi. handleEmbeddingException seedhe
 * ex.getHttpStatus() padhta hai - EmbeddingException already har
 * failure kind ke liye sahi status carry karta hai (uska javadoc
 * dekho), isliye ek hi handler method sab nau EmbeddingErrorCodes ko
 * cover karta hai.
 * Ye kyu hai: is platform ka established exception-handling convention,
 * Phase 3.4 par apply kiya gaya.
 * Dusre components se kaise communicate karta hai: ye khud backend-side
 * code hai jis par har EmbeddingController method apne try/catch
 * blocks ke bajaye implicitly rely karta hai.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmbeddingException(EmbeddingException ex, HttpServletRequest request) {
        log.warn("Embedding request failed path={} errorCode={} retryable={} status={}",
                request.getRequestURI(), ex.getErrorCode(), ex.isRetryable(), ex.getHttpStatus());
        return buildResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), String.valueOf(fe.getRejectedValue()), fe.getDefaultMessage()))
                .toList();
        ErrorResponse error = ErrorResponse.withFieldErrors(ErrorCodes.VALIDATION_ERROR, "Request validation failed", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(PaymentXException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentXException(PaymentXException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.error(ErrorResponse.of(errorCode, message, request.getRequestURI())));
    }
}
