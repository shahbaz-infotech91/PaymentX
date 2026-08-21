package com.paymentx.gateway.exception;

import com.paymentx.common.constant.ErrorCodes;
import com.paymentx.common.dto.ApiResponse;
import com.paymentx.common.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Circuit-breaker fallback target (routes configure a
 * fallbackUri="forward:/fallback" - see GatewayConfig). Reached when the
 * circuit is OPEN or the call to a backend service times out/errors
 * beyond the configured threshold - returns a clean 503 rather than
 * letting the raw connection failure leak to the caller.
 */
@RestController
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * FallbackController is a REST controller in the gateway module of PaymentX. It lives in package com.paymentx.gateway.exception and participates in gateway's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through gateway's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * FallbackController PaymentX ke gateway module ka ek REST controller hai. Ye com.paymentx.gateway.exception package me hai aur gateway ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise gateway ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class FallbackController {

    @RequestMapping("/fallback")
    public ResponseEntity<ApiResponse<Void>> fallback(ServerWebExchange exchange) {
        ApiResponse<Void> body = ApiResponse.error(ErrorResponse.of(
                ErrorCodes.SERVICE_UNAVAILABLE,
                "The requested service is temporarily unavailable. Please retry shortly.",
                exchange.getRequest().getPath().value()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
