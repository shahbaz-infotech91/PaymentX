package com.paymentx.prompt.filter;

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
 * Matches Routing Service's/Validation Service's/Payment Service's own
 * CorrelationIdFilter exactly (each servlet-based PaymentX service owns
 * its own copy - not shared via paymentx-common-library, per this
 * platform's existing architecture decision). Reads X-Correlation-Id
 * from the incoming request (propagated by API Gateway, or by AI Chat
 * Service in the Control Center backend once it calls this service in
 * a future phase) or generates one if absent, puts it in MDC so every
 * log line for this request includes it, echoes it back on the
 * response header.
 * Why it exists: Step 17 of the Phase 3.2 brief - "preserve correlation
 * ID, trace ID" - and Step 14's audit-friendly logging requirement
 * (every log line PromptServiceImpl writes is tagged with this).
 * How it communicates with other components: every controller in this
 * module runs behind it; GlobalExceptionHandler and PromptServiceImpl's
 * log lines both pick up the MDC value automatically via the logging
 * pattern in application.yml.
 *
 * Hinglish:
 * Routing Service/Validation Service/Payment Service ke apne
 * CorrelationIdFilter se exactly match karta hai (har servlet-based
 * PaymentX service apni khud ki copy rakhti hai - paymentx-common-
 * library se shared nahi, is platform ke existing architecture
 * decision ke hisaab se). Incoming request se (API Gateway dwara
 * propagate kiya gaya, ya future phase me jab Control Center backend
 * ki AI Chat Service is service ko call karegi) X-Correlation-Id
 * padhta hai ya agar na ho toh ek generate karta hai, ise MDC me daalta
 * hai taaki is request ki har log line me wo shamil ho, response header
 * par wapas echo karta hai.
 * Ye kyu hai: Phase 3.2 brief ka Step 17 - "correlation ID, trace ID
 * preserve karo" - aur Step 14 ki audit-friendly logging requirement
 * (PromptServiceImpl jo bhi log line likhta hai wo isi se tagged hoti
 * hai).
 * Dusre components se kaise communicate karta hai: is module ka har
 * controller iske peeche chalta hai; GlobalExceptionHandler aur
 * PromptServiceImpl ki log lines dono application.yml ke logging
 * pattern ke through automatically MDC value pick karti hain.
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
