package com.paymentx.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentx.common.exception.ForbiddenException;
import com.paymentx.common.exception.PaymentXException;
import com.paymentx.common.exception.ResourceNotFoundException;
import com.paymentx.common.exception.UnauthorizedException;
import com.paymentx.common.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression-remediation fix (Defect 4): covers
 * com.paymentx.gateway.exception.GlobalExceptionHandler's error-mapping table -
 * the platform-wide contract that every exception type reaching the gateway maps
 * to a specific, stable HTTP status and error code (relied on by every client of
 * the gateway's JSON error envelope, ApiResponse/ErrorResponse).
 */
class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(objectMapper);

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments/123").build());
    }

    @Test
    void resourceNotFoundException_mapsTo404() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new ResourceNotFoundException("Payment", "123")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void validationException_mapsTo400() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new ValidationException("amount must be positive")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("VALIDATION_ERROR");
    }

    @Test
    void unauthorizedException_mapsTo401() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new UnauthorizedException("token expired")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("UNAUTHORIZED");
    }

    @Test
    void forbiddenException_mapsTo403() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new ForbiddenException("role not permitted")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("FORBIDDEN");
    }

    @Test
    void paymentXException_usesItsOwnErrorCodeAndMapsTo422() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new PaymentXException("DOWNSTREAM_REJECTED", "downstream service rejected", false)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("DOWNSTREAM_REJECTED");
    }

    @Test
    void unmappedException_mapsTo500WithGenericInternalErrorCode() {
        MockServerWebExchange exchange = exchange();
        handler.handle(exchange, new RuntimeException("unexpected")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("INTERNAL_ERROR");
    }
}
