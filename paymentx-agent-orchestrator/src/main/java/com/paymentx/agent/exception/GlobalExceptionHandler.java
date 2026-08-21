package com.paymentx.agent.exception;

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
 * Matches every other AI Platform service's GlobalExceptionHandler
 * pattern exactly - converts any exception that escapes
 * controller/AgentController into the same ApiResponse&lt;Void&gt; error
 * shape every PaymentX service uses, never a raw stack trace. In
 * practice, orchestrator/AgentOrchestratorService already converts
 * every real AgentException into an honest AgentExecuteResponse
 * (status=FAILED) itself rather than letting it propagate - this
 * handler is the safety net for genuinely unexpected failures (request
 * validation, a bug) that occur outside that try/catch.
 * Why it exists: this platform's established exception-handling
 * convention, applied to Phase 3.8.
 *
 * Hinglish:
 * Har doosri AI Platform service ke GlobalExceptionHandler pattern se
 * exactly match karta hai - controller/AgentController se escape hui
 * kisi bhi exception ko usi ApiResponse&lt;Void&gt; error shape me
 * convert karta hai jo har PaymentX service use karti hai, kabhi ek raw
 * stack trace nahi. Practically, orchestrator/AgentOrchestratorService
 * pehle se hi har real AgentException ko khud ek honest
 * AgentExecuteResponse (status=FAILED) me convert kar deta hai, use
 * propagate hone dene ke bajaye - ye handler genuinely unexpected
 * failures (request validation, ek bug) ke liye safety net hai jo us
 * try/catch se bahar hoti hain.
 * Ye kyu hai: is platform ka established exception-handling convention,
 * Phase 3.8 par apply kiya gaya.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentException.class)
    public ResponseEntity<ApiResponse<Void>> handleAgentException(AgentException ex, HttpServletRequest request) {
        log.warn("Agent Orchestrator request failed path={} errorCode={} retryable={} status={}",
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
