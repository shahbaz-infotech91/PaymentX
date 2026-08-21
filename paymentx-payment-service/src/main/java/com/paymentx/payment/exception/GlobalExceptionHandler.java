package com.paymentx.payment.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.ConflictException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * WHY this reuses ApiResponse/ErrorResponse/the exception hierarchy from
 * paymentx-common-library entirely, with zero locally-defined
 * equivalents: this is a servlet (Spring MVC) application, unlike API
 * Gateway's reactive ErrorWebExceptionHandler - the underlying stack
 * differs (@RestControllerAdvice vs ErrorWebExceptionHandler), but the
 * response SHAPE is identical because both consume the exact same
 * common-library DTOs. A client calling either service (or Gateway
 * proxying to either) sees one consistent error contract platform-wide.
 */
@RestControllerAdvice
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler is a REST controller in the payment module of PaymentX. It lives in package com.paymentx.payment.exception and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler PaymentX ke payment module ka ek REST controller hai. Ye com.paymentx.payment.exception package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    /** Jakarta Bean Validation failures on @Valid @RequestBody DTOs
     *  (PaymentRetryRequest/CancellationRequest). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(),
                        String.valueOf(fe.getRejectedValue()), fe.getDefaultMessage()))
                .toList();

        ErrorResponse error = ErrorResponse.withFieldErrors(
                ErrorCodes.VALIDATION_ERROR, "Request validation failed", request.getRequestURI(), fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Any other PaymentXException subtype not explicitly mapped above -
     *  e.g. a future exception type added to common-library that this
     *  service doesn't yet special-case. */
    @ExceptionHandler(PaymentXException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentXException(PaymentXException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception path={}", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String errorCode, String message,
                                                              HttpServletRequest request,
                                                              List<ErrorResponse.FieldError> fieldErrors) {
        ErrorResponse error = fieldErrors == null
                ? ErrorResponse.of(errorCode, message, request.getRequestURI())
                : ErrorResponse.withFieldErrors(errorCode, message, request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(ApiResponse.error(error));
    }
}
