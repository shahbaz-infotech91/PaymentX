package com.paymentx.llm.exception;

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
 * Matches Prompt Service's/Routing Service's GlobalExceptionHandler
 * pattern exactly - one place converting every exception thrown
 * anywhere in this service into the same ApiResponse&lt;Void&gt; error
 * shape, never a raw Spring stack trace and never a fabricated success
 * body (Step 36's "no fake AI" rule applies to error paths too: a
 * caller must always be able to tell "the provider genuinely answered"
 * apart from "something failed", never a silently-swallowed exception
 * masquerading as a normal response). handleLlmException reads
 * ex.getHttpStatus() directly - LlmException already carries the
 * correct status per failure kind (see its javadoc), so one handler
 * method covers all eight LlmErrorCodes instead of eight near-identical
 * handler methods.
 * Why it exists: Step 19-equivalent of this platform's established
 * exception-handling convention, applied to Phase 3.3.
 * How it communicates with other components: this IS backend-side code
 * every LlmController method implicitly relies on instead of its own
 * try/catch blocks; the ApiResponse/ErrorResponse shape it produces is
 * the exact one AI Chat Service will parse once it calls this service
 * for real (Step 10 of the Phase 3.3 brief).
 *
 * Hinglish:
 * Prompt Service/Routing Service ke GlobalExceptionHandler pattern se
 * exactly match karta hai - ek jagah jo is service me kahin bhi throw
 * hui har exception ko usi ApiResponse&lt;Void&gt; error shape me
 * convert karti hai, kabhi ek raw Spring stack trace nahi aur kabhi ek
 * fabricated success body nahi (Step 36 ka "no fake AI" rule error
 * paths par bhi lagu hota hai: ek caller ko hamesha "provider ne
 * genuinely jawab diya" ko "kuch fail hua" se alag bata sakna chahiye,
 * kabhi ek silently-swallowed exception jo normal response jaisa dikhe
 * nahi). handleLlmException seedhe ex.getHttpStatus() padhta hai -
 * LlmException already har failure kind ke liye sahi status carry
 * karta hai (uska javadoc dekho), isliye ek hi handler method sab aath
 * LlmErrorCodes ko cover karta hai, aath near-identical handler methods
 * ke bajaye.
 * Ye kyu hai: is platform ke established exception-handling convention
 * ka Step 19-equivalent, Phase 3.3 par apply kiya gaya.
 * Dusre components se kaise communicate karta hai: ye khud backend-side
 * code hai jis par har LlmController method apne try/catch blocks ke
 * bajaye implicitly rely karta hai; jo ApiResponse/ErrorResponse shape
 * ye produce karta hai wahi exact shape hai jise AI Chat Service parse
 * karegi ek baar wo is service ko real me call karegi (Phase 3.3 brief
 * ka Step 10).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiResponse<Void>> handleLlmException(LlmException ex, HttpServletRequest request) {
        log.warn("LLM request failed path={} errorCode={} retryable={} status={}",
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
