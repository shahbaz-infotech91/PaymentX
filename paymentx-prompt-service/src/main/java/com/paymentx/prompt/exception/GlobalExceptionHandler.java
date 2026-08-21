package com.paymentx.prompt.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.PaymentXException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * English:
 * Matches Routing Service's GlobalExceptionHandler pattern exactly -
 * one place converting every exception thrown anywhere in this
 * service into the same ApiResponse&lt;Void&gt; error shape, never a
 * raw Spring stack trace. WHY PromptNotFoundException gets its own
 * handler (404) ahead of the generic PaymentXException handler (422):
 * Spring resolves the most specific matching @ExceptionHandler method
 * automatically, so ordering in this file doesn't matter to Spring,
 * but PromptNotFoundException genuinely needs a different HTTP status
 * than every other PaymentXException this service throws (missing
 * variable / unknown variable / invalid status are all "well-formed
 * request, business rule violation" = 422; "the thing doesn't exist" =
 * 404) - two handler methods, not one, is what makes that distinction
 * real rather than folding every PaymentXException into one status
 * code.
 * Why it exists: Step 19 of the Phase 3.2 brief - "use existing
 * PaymentX error response conventions... do NOT expose stack traces."
 * How it communicates with other components: this IS backend-side
 * code every PromptController method implicitly relies on instead of
 * its own try/catch blocks; the ApiResponse/ErrorResponse shape it
 * produces is the exact one AI Chat Service (and, later, LLM Service)
 * will parse when this service starts being called for real.
 *
 * Hinglish:
 * Routing Service ke GlobalExceptionHandler pattern se exactly match
 * karta hai - ek jagah jo is service me kahin bhi throw hui har
 * exception ko usi ApiResponse&lt;Void&gt; error shape me convert
 * karti hai, kabhi ek raw Spring stack trace nahi. PromptNotFoundException
 * ko apna alag handler (404) generic PaymentXException handler (422)
 * se pehle KYU milta hai: Spring khud automatically sabse specific
 * matching @ExceptionHandler method resolve karta hai, isliye is file
 * me ordering Spring ke liye matter nahi karti, lekin PromptNotFoundException
 * ko genuinely ek alag HTTP status chahiye is service ke throw kiye har
 * doosre PaymentXException se (missing variable / unknown variable /
 * invalid status sab "well-formed request, business rule violation" =
 * 422 hain; "cheez exist hi nahi karti" = 404) - do handler methods, ek
 * nahi, yehi hai jo us distinction ko real banata hai, har
 * PaymentXException ko ek hi status code me fold karne ke bajaye.
 * Ye kyu hai: Phase 3.2 brief ka Step 19 - "existing PaymentX error
 * response conventions use karo... stack traces expose mat karo."
 * Dusre components se kaise communicate karta hai: ye khud backend-side
 * code hai jis par har PromptController method apne try/catch blocks
 * ke bajaye implicitly rely karta hai; jo ApiResponse/ErrorResponse
 * shape ye produce karta hai wahi exact shape hai jise AI Chat Service
 * (aur, baad me, LLM Service) parse karenge jab ye service real me call
 * hona shuru hogi.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePromptNotFound(PromptNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request);
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

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access denied", request);
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
