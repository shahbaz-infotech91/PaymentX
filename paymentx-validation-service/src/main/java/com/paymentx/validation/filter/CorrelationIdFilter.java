package com.paymentx.validation.filter;

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
 * services (this one, Payment Service) AND a reactive service (API
 * Gateway) must not contain servlet-specific infrastructure at all. This
 * filter is now duplicated (not shared) between Validation Service and
 * Payment Service - a deliberate, small amount of duplication in
 * exchange for a shared library with zero web-framework assumptions.
 *
 * WHY MDC_KEY ("correlationId") is DIFFERENT from
 * HeaderConstants.CORRELATION_ID ("X-Correlation-Id"): one is the HTTP
 * header name on the wire, the other is the MDC key logback's pattern
 * (%X{correlationId} in application.yml) looks up by. Putting the header
 * name string into MDC instead of this constant would silently produce
 * empty correlationId in every log line - the pattern would simply never
 * find a key named "X-Correlation-Id". MDC_KEY is public so
 * non-HTTP-triggered code (Kafka consumers, schedulers - see
 * util.LoggingContext) can populate the exact same MDC key explicitly,
 * keeping one consistent correlation key regardless of trigger source.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
/**
 * ====================================================================
 * ENGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter is a servlet filter in the validation module of PaymentX. It lives in package com.paymentx.validation.filter and participates in validation's internal request/data flow, used by other classes in this module (and, where applicable, consumed indirectly by other PaymentX services through validation's REST API or Kafka events).
 *
 * ====================================================================
 * HINGLISH
 * --------------------------------------------------------------------
 * CorrelationIdFilter PaymentX ke validation module ka ek servlet filter hai. Ye com.paymentx.validation.filter package me hai aur validation ke internal request/data flow ka hissa hai, isi module ki dusri classes ise use karti hain (aur jahan applicable ho, dusri PaymentX services ise validation ke REST API ya Kafka events ke through indirectly use karti hain).
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
