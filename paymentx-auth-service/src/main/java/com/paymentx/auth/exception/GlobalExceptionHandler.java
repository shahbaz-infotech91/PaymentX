package com.paymentx.auth.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Mirrors every other service's GlobalExceptionHandler shape (see e.g.
 * Reporting Service's), reusing common-library's shared exception
 * hierarchy. One addition no other service needed before this module:
 * an explicit UnauthorizedException -> 401 handler. Without it,
 * UnauthorizedException (which extends PaymentXException) would be
 * caught by the generic PaymentXException handler and incorrectly
 * return 422 instead of 401 - Spring resolves @ExceptionHandler methods
 * to the most specific matching type, so this handler correctly takes
 * precedence for this one subtype.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage(), request);
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

    /**
     * Without this, the generic Exception.class handler below (necessarily
     * broad, so real unexpected errors don't leak stack traces) would also
     * catch Spring MVC's own NoResourceFoundException - thrown for a
     * genuinely unmapped path - and incorrectly turn a real 404 into a
     * 500. Spring resolves @ExceptionHandler methods to the most specific
     * matching type, so this explicit handler correctly takes precedence.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ErrorCodes.RESOURCE_NOT_FOUND, "No such endpoint", request);
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
