package com.paymentx.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * WHY this exists and why it lives in `common`, not in each service:
 * --------------------------------------------------------------
 * Trace a real incident: "Payment X failed for merchant Y at 3:47am."
 * That request touches API Gateway -> Auth -> Validation -> Kafka ->
 * Payment Service -> Routing -> InstantPayment adapter -> Kafka ACK bus -> Audit.
 *
 * Without a correlation ID threaded through EVERY log line at EVERY hop,
 * finding "all log lines for this one payment" means grepping timestamps
 * across 8 services and guessing. With MDC + a shared correlation ID header,
 * one grep for `traceId=abc-123` across your log aggregator (we'll wire
 * this to CloudWatch/ELK later) reconstructs the entire journey instantly.
 *
 * This filter:
 *  1. Reads X-Correlation-Id from the incoming request if present
 *     (propagated from an upstream service or the API Gateway)
 *  2. Generates one if absent (this service is the origin of the request)
 *  3. Puts it into SLF4J's MDC (Mapped Diagnostic Context) so every log
 *     statement in this request's thread automatically includes it
 *     (requires the logging pattern to reference %X{correlationId} -
 *     we'll configure that in logback-spring.xml per service)
 *  4. Writes it back onto the response header AND propagates it to any
 *     outbound Kafka message or downstream HTTP call.
 *
 * @Order(Ordered.HIGHEST_PRECEDENCE) ensures this runs before Spring
 * Security filters, so even auth-rejected requests are traceable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter is a servlet filter in the common module of PaymentX. It lives in package com.paymentx.common.logging and participates in common's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through common's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter PaymentX ke common module ka ek servlet filter hai. Ye com.paymentx.common.logging package me hai aur common ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise common ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always clear MDC. Threads are pooled and reused
            // (Tomcat's request-handling thread pool) - if you don't clear
            // this, the NEXT unrelated request handled by the same thread
            // will incorrectly log under the PREVIOUS request's correlation
            // ID. This is a subtle bug that only shows up under load, and
            // it will send you on a wild goose chase in production.
            MDC.remove(MDC_KEY);
        }
    }
}
