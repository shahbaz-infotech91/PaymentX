package com.paymentx.reporting.filter;

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
 * ====================================================================
 * ENGLISH: Generates/propagates the correlation ID for every HTTP
 * request into MDC, so every log line for this request carries it -
 * the explicit "Correlation ID" structured-logging requirement. Each
 * servlet-based service owns its own copy (see the architecture
 * cleanup that removed this from paymentx-common-library entirely).
 *
 * HINGLISH: Har HTTP request ke liye correlation ID generate/propagate
 * karta hai MDC me, taaki us request ki har log line me ye ho -
 * explicit "Correlation ID" structured-logging requirement. Har
 * servlet-based service apni copy rakhti hai.
 * ====================================================================
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
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
