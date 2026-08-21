package com.paymentx.llm.config;

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
 * English:
 * Matches Prompt Service's/Routing Service's CorrelationIdFilter
 * exactly (each servlet-based PaymentX service owns its own copy - not
 * shared via paymentx-common-library, per this platform's existing
 * architecture decision). Reads X-Correlation-Id from the incoming
 * request (propagated by AI Chat Service once Step 10 wires it up, or
 * directly by a test/curl caller today) or generates one if absent,
 * puts it in MDC so every log line for this request includes it,
 * echoes it back on the response header.
 * Why it exists: Step 15's "preserve correlation ID, trace ID" and this
 * platform's audit-friendly logging convention - every log line
 * LlmServiceImpl/AnthropicLlmProvider write is tagged with this.
 * How it communicates with other components: every controller in this
 * module runs behind it; GlobalExceptionHandler's log lines pick up the
 * MDC value automatically via the logging pattern in application.yml.
 *
 * Hinglish:
 * Prompt Service/Routing Service ke CorrelationIdFilter se exactly
 * match karta hai (har servlet-based PaymentX service apni khud ki copy
 * rakhti hai - paymentx-common-library se shared nahi, is platform ke
 * existing architecture decision ke hisaab se). Incoming request se
 * (ek baar Step 10 wire hone par AI Chat Service dwara propagate kiya
 * gaya, ya aaj seedhe ek test/curl caller dwara) X-Correlation-Id
 * padhta hai ya agar na ho toh ek generate karta hai, ise MDC me daalta
 * hai taaki is request ki har log line me wo shamil ho, response header
 * par wapas echo karta hai.
 * Ye kyu hai: Step 15 ki "correlation ID, trace ID preserve karo" aur is
 * platform ka audit-friendly logging convention - LlmServiceImpl/
 * AnthropicLlmProvider jo bhi log line likhte hain wo isi se tagged
 * hoti hai.
 * Dusre components se kaise communicate karta hai: is module ka har
 * controller iske peeche chalta hai; GlobalExceptionHandler ki log
 * lines application.yml ke logging pattern ke through automatically
 * MDC value pick karti hain.
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
