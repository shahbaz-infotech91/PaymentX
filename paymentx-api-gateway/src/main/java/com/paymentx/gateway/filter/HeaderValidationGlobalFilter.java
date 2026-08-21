package com.paymentx.gateway.filter;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WHY only Content-Type is validated for state-changing methods, not a
 * broader set of "required headers": the gateway does not know any
 * individual backend service's business-payload shape (that validation
 * belongs to Validation Service, which already implements it thoroughly
 * - see its ValidationController/PaymentValidationRequest). This filter
 * catches only the class of malformed request that is meaningful at the
 * gateway layer itself: a POST/PUT/PATCH with no or wrong Content-Type
 * would fail identically at every downstream service, so rejecting it
 * here saves a wasted network hop.
 */
@Component
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * HeaderValidationGlobalFilter is a servlet filter in the gateway module of PaymentX. It lives in package com.paymentx.gateway.filter and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * HeaderValidationGlobalFilter PaymentX ke gateway module ka ek servlet filter hai. Ye com.paymentx.gateway.filter package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class HeaderValidationGlobalFilter implements GlobalFilter, Ordered {

    private static final List<HttpMethod> BODY_METHODS = List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH);

    private final ObjectMapper objectMapper;

    public HeaderValidationGlobalFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (BODY_METHODS.contains(method)) {
            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
            if (contentType == null) {
                return rejectMissingContentType(exchange);
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> rejectMissingContentType(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(
                ErrorCodes.VALIDATION_ERROR,
                "Content-Type header is required for this request method",
                exchange.getRequest().getPath().value()));

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 103;
    }
}
