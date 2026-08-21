package com.paymentx.vector.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.PaymentXException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * English:
 * Matches LLM/Embedding/Prompt Service's GlobalExceptionHandler pattern
 * exactly - one place converting every exception thrown anywhere in
 * this service into the same ApiResponse&lt;Void&gt; error shape, never
 * a raw Spring stack trace and never a fabricated success body.
 * handleVectorException reads ex.getHttpStatus() directly -
 * VectorException already carries the correct status per failure kind
 * (see its javadoc). handleDataIntegrityViolation is new relative to
 * the stateless AI services (LLM/Embedding) - this service is the first
 * AI Platform service since Prompt Service to own real database
 * UNIQUE/FK constraints, and a concurrent duplicate insert racing past
 * an application-level existence check (Step 25 - "two ingestion
 * requests for the same document/chunk must not create inconsistent
 * duplicate records") surfaces here as a real
 * DataIntegrityViolationException from the database's own unique
 * index, not as an application-thrown VectorException - both must map
 * to the same honest 409, not an unhandled 500.
 * Why it exists: this platform's established exception-handling
 * convention, applied to Phase 3.5, extended for this service's real
 * database-constraint surface.
 * How it communicates with other components: this IS backend-side code
 * every VectorController method implicitly relies on instead of its
 * own try/catch blocks.
 *
 * Hinglish:
 * LLM/Embedding/Prompt Service ke GlobalExceptionHandler pattern se
 * exactly match karta hai - ek jagah jo is service me kahin bhi throw
 * hui har exception ko usi ApiResponse&lt;Void&gt; error shape me
 * convert karti hai, kabhi ek raw Spring stack trace nahi aur kabhi ek
 * fabricated success body nahi. handleVectorException seedhe
 * ex.getHttpStatus() padhta hai - VectorException already har failure
 * kind ke liye sahi status carry karta hai (uska javadoc dekho).
 * handleDataIntegrityViolation stateless AI services (LLM/Embedding) ke
 * against naya hai - ye service Prompt Service ke baad pehli AI
 * Platform service hai jo real database UNIQUE/FK constraints rakhti
 * hai, aur ek concurrent duplicate insert jo ek application-level
 * existence check ko race kar jaaye (Step 25 - "same document/chunk ke
 * liye do ingestion requests inconsistent duplicate records create
 * nahi karni chahiye") yahan ek real DataIntegrityViolationException
 * ke roop me surface hoti hai database ke apne unique index se, ek
 * application-thrown VectorException ke roop me nahi - dono ko usi
 * honest 409 par map hona chahiye, ek unhandled 500 par nahi.
 * Ye kyu hai: is platform ka established exception-handling convention,
 * Phase 3.5 par apply kiya gaya, is service ke real database-constraint
 * surface ke liye extend kiya gaya.
 * Dusre components se kaise communicate karta hai: ye khud backend-side
 * code hai jis par har VectorController method apne try/catch blocks
 * ke bajaye implicitly rely karta hai.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(VectorException.class)
    public ResponseEntity<ApiResponse<Void>> handleVectorException(VectorException ex, HttpServletRequest request) {
        log.warn("Vector request failed path={} errorCode={} status={}", request.getRequestURI(), ex.getErrorCode(), ex.getHttpStatus());
        return buildResponse(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Vector request violated a database constraint path={} reason={}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, ErrorCodes.CONFLICT,
                "This document/chunk/embedding already exists or violates a database constraint.", request);
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
