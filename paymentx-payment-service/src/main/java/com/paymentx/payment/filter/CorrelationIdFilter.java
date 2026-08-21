package com.paymentx.payment.filter;

import com.paymentx.common.constant.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
 * WHY this class lives HERE and not in paymentx-common-library:
 * architecture cleanup - a shared library consumed by BOTH servlet-based
 * services (this one, Validation Service) AND a reactive service (API
 * Gateway) must not contain servlet-specific infrastructure at all. This
 * filter is duplicated (not shared) between Payment Service and
 * Validation Service - a deliberate, small amount of duplication in
 * exchange for a shared library with zero web-framework assumptions.
 *
 * WHY MDC_KEY ("correlationId") is DIFFERENT from
 * HeaderConstants.CORRELATION_ID ("X-Correlation-Id"): one is the HTTP
 * header name on the wire, the other is the MDC key logback's pattern
 * (%X{correlationId} in application.yml) looks up by.
 *
 * util.LoggingContext (Kafka consumer / scheduler MDC population)
 * references this class's MDC_KEY as its single source of truth, so an
 * HTTP-triggered request and a Kafka-consumer-triggered flow produce log
 * lines under the exact same correlationId key.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter is a servlet filter in the payment module of PaymentX. It lives in package com.paymentx.payment.filter and participates in payment's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through payment's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter PaymentX ke payment module ka ek servlet filter hai. Ye com.paymentx.payment.filter package me hai aur payment ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise payment ke REST API ya Kafka events ke through indirectly use karti hain).
 * ====================================================================
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HeaderConstants.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HeaderConstants.CORRELATION_ID, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
