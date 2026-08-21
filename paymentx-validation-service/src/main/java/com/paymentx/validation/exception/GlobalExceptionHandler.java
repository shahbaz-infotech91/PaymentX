package com.paymentx.validation.exception;

import com.paymentx.common.exception.PaymentXException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * WHY @RestControllerAdvice instead of try/catch in every controller method:
 * Centralizes error-response SHAPE. Every error from this service - schema
 * failure, business rule failure, unexpected exception - returns the same
 * JSON structure (errorCode, message, timestamp). A client (or the API
 * Gateway) integrating with this service writes ONE error-parsing code
 * path, not one per endpoint per exception type.
 */
@RestControllerAdvice
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler is a REST controller in the validation module of PaymentX. It lives in package com.paymentx.validation.exception and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler PaymentX ke validation module ka ek REST controller hai. Ye com.paymentx.validation.exception package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class GlobalExceptionHandler {

    /** Jakarta Bean Validation failures on @Valid @RequestBody - schema layer. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Invalid request payload");

        return buildResponse(HttpStatus.BAD_REQUEST, "SCHEMA_VALIDATION_FAILED", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "SCHEMA_VALIDATION_FAILED", ex.getMessage());
    }

    /** Our own domain exceptions - business rule failures. */
    @ExceptionHandler(PaymentXException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentXException(PaymentXException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage());
    }

    /** Catch-all - anything unexpected. Deliberately generic message to the
     *  client (never leak stack traces / internal details in a payment API
     *  response - that's an information-disclosure risk), full detail goes
     *  to logs only. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred while processing the request");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String errorCode, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("errorCode", errorCode);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
