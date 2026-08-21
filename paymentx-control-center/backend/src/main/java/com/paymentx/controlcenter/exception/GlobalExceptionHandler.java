package com.paymentx.controlcenter.exception;

import com.paymentx.controlcenter.dto.ApiResponse;
import com.paymentx.controlcenter.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ENGLISH: Converts every exception thrown anywhere in this backend into
 * the same ApiResponse<Void> error shape, instead of leaking a raw
 * Spring stack trace to the frontend. What it does: maps
 * ControlCenterException to its own errorCode, validation failures to a
 * VALIDATION_ERROR code, and anything unexpected to an
 * INTERNAL_ERROR code - logging the real exception server-side in every
 * case. Why it exists: mirrors the same @RestControllerAdvice pattern
 * every existing PaymentX service already uses (e.g. Routing Service's
 * GlobalExceptionHandler), kept as a small independent copy here per
 * Phase 1's no-shared-library-dependency constraint. How it will
 * communicate with the backend: this IS backend-side code - it's what
 * every controller in this module implicitly relies on instead of its
 * own try/catch blocks.
 *
 * HINGLISH: Ye is backend me kahin bhi throw hui har exception ko usi
 * ApiResponse<Void> error shape me convert karta hai, taaki frontend ko
 * raw Spring stack trace kabhi na dikhe. Ye kya karti hai:
 * ControlCenterException ko uske apne errorCode par, validation
 * failures ko VALIDATION_ERROR code par, aur kisi bhi unexpected cheez
 * ko INTERNAL_ERROR code par map karta hai - har case me real exception
 * ko server-side log karte hue. Ye dashboard me kyu hai: existing
 * PaymentX services (jaise Routing Service ka GlobalExceptionHandler)
 * jo @RestControllerAdvice pattern already use karte hain, usi ko
 * mirror karta hai, Phase 1 ke no-shared-library-dependency constraint
 * ke hisaab se ek chhoti independent copy ke roop me. Backend se kaise
 * connect hogi: ye khud backend-side code hai - is module ka har
 * controller apne try/catch blocks ke bajaye implicitly isi par
 * depend karta hai.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Phase 3.1: checked ahead of the generic ControlCenterException handler below (Spring resolves the
    // most specific matching @ExceptionHandler automatically) because "AI not built yet" is a 503 Service
    // Unavailable, not a 502 Bad Gateway - there is no upstream call that failed, there is simply no real
    // AI backend behind this contract yet.
    @ExceptionHandler(AiServiceNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiServiceNotReady(AiServiceNotReadyException ex, HttpServletRequest request) {
        log.info("AiServiceNotReadyException errorCode={} path={}", ex.getErrorCode(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI())));
    }

    @ExceptionHandler(ControlCenterException.class)
    public ResponseEntity<ApiResponse<Void>> handleControlCenterException(ControlCenterException ex, HttpServletRequest request) {
        log.warn("ControlCenterException errorCode={} path={}", ex.getErrorCode(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(new ErrorResponse(ex.getErrorCode(), ex.getMessage(), request.getRequestURI())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(new ErrorResponse("BAD_REQUEST", ex.getMessage(), request.getRequestURI())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Request validation failed"
                : ex.getBindingResult().getFieldErrors().get(0).getField() + ": " + ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(new ErrorResponse("VALIDATION_ERROR", message, request.getRequestURI())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", request.getRequestURI())));
    }
}
