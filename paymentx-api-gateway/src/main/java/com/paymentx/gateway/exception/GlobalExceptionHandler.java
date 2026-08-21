package com.paymentx.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.paymentx.common.exception.ForbiddenException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.common.exception.UnauthorizedException;
import com.paymentx.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * WHY ErrorWebExceptionHandler (not @ControllerAdvice/@ExceptionHandler):
 * Spring Cloud Gateway is not a @Controller-based application - there is
 * no DispatcherHandler-routed MVC layer for @ExceptionHandler to attach
 * to. ErrorWebExceptionHandler is the WebFlux-native mechanism for
 * catching exceptions that occur anywhere in the reactive chain
 * (filters, routing, the actual proxied call).
 *
 * WHY @Order(-2): Spring Boot registers its own default
 * DefaultErrorWebExceptionHandler at a specific precedence; a lower
 * (more negative) order here ensures this handler takes priority over
 * that default, matching Spring Boot's documented convention for
 * overriding WebFlux error handling.
 *
 * Reuses ApiResponse/ErrorResponse/the exception hierarchy from
 * paymentx-common-library directly - no duplicate DTOs or exception
 * types defined in this module.
 */
@Component
@Order(-2)
@Slf4j
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler is a exception in the gateway module of PaymentX. It lives in package com.paymentx.gateway.exception and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * GlobalExceptionHandler PaymentX ke gateway module ka ek exception hai. Ye com.paymentx.gateway.exception package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // Guard against double-signal edge cases (e.g. an upstream filter
        // erroring after it has already written and committed a response):
        // once committed, response headers become read-only, so attempting
        // to set status/headers again throws UnsupportedOperationException
        // and forces Reactor Netty to abort the connection mid-stream,
        // corrupting whatever had already been sent. Matches the same
        // isCommitted() guard Spring Boot's own DefaultErrorWebExceptionHandler
        // applies before writing an error response.
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = resolveStatus(ex);
        String errorCode = resolveErrorCode(ex, status);
        String message = ex.getMessage() != null ? ex.getMessage() : status.getReasonPhrase();

        if (status.is5xxServerError()) {
            log.error("Unhandled exception at gateway path={}", exchange.getRequest().getPath(), ex);
        } else {
            log.warn("Request rejected path={} status={} message={}",
                    exchange.getRequest().getPath(), status, message);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(
                ErrorResponse.of(errorCode, message, exchange.getRequest().getPath().value()));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationError) {
            bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResourceNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ValidationException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof UnauthorizedException || ex instanceof BadCredentialsException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof ForbiddenException || ex instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof PaymentXException) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveErrorCode(Throwable ex, HttpStatus status) {
        if (ex instanceof PaymentXException paymentXException) {
            return paymentXException.getErrorCode();
        }
        return switch (status) {
            case NOT_FOUND -> ErrorCodes.RESOURCE_NOT_FOUND;
            case BAD_REQUEST -> ErrorCodes.VALIDATION_ERROR;
            case UNAUTHORIZED -> ErrorCodes.UNAUTHORIZED;
            case FORBIDDEN -> ErrorCodes.FORBIDDEN;
            default -> ErrorCodes.INTERNAL_ERROR;
        };
    }
}
