package com.paymentx.mcp.config;

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
 * Matches every other AI Platform service's CorrelationIdFilter exactly
 * (each servlet-based PaymentX service owns its own copy - not shared
 * via paymentx-common-library). Reads X-Correlation-Id from the
 * incoming request or generates one if absent, puts it in MDC so every
 * log line for this request includes it, echoes it back on the
 * response header. Runs as a normal Spring Boot auto-registered global
 * servlet filter, so it applies to EVERY request this service receives
 * - including requests to the manually-registered MCP protocol servlet
 * (config/McpServerConfig.java's HttpServletStreamableServerTransportProvider,
 * mounted at /mcp), not just to @RestController-handled requests. This is
 * the one real mechanism behind Step 31's distributed-trace requirement:
 * AI/Agent -> MCP Gateway -> Payment/Routing/Audit/Reconciliation Service
 * all carry the same correlation ID end to end, without introducing a
 * second tracing system.
 * Why it exists: Step 30/31's structured-logging and distributed-trace
 * requirements.
 * How it communicates with other components: registered globally by
 * Spring Boot (this class is a @Component implementing OncePerRequestFilter);
 * config/McpServerConfig's transport-context extractor reads this same
 * request header directly (not via MDC, to stay reliable across the
 * SDK's internal async dispatch - see that class's javadoc) and forwards
 * it to every downstream client call in client/.
 *
 * Hinglish:
 * Har doosri AI Platform service ke CorrelationIdFilter se exactly
 * match karta hai (har servlet-based PaymentX service apni khud ki copy
 * rakhti hai - paymentx-common-library se shared nahi). Incoming
 * request se X-Correlation-Id padhta hai ya agar na ho toh ek generate
 * karta hai, ise MDC me daalta hai taaki is request ki har log line me
 * wo shamil ho, response header par wapas echo karta hai. Ek normal
 * Spring Boot auto-registered global servlet filter ki tarah chalta hai,
 * isliye ye is service ki HAR request par apply hota hai - including
 * manually-registered MCP protocol servlet
 * (config/McpServerConfig.java ka HttpServletStreamableServerTransportProvider,
 * /mcp par mounted) ki requests, sirf @RestController-handled requests
 * nahi. Ye Step 31 ki distributed-trace requirement ke peeche yahi ek
 * real mechanism hai: AI/Agent -> MCP Gateway -> Payment/Routing/Audit/
 * Reconciliation Service sab end to end same correlation ID carry karte
 * hain, bina ek doosri tracing system introduce kiye.
 * Ye kyu hai: Step 30/31 ki structured-logging aur distributed-trace
 * requirements.
 * Dusre components se kaise communicate karta hai: Spring Boot dwara
 * globally register hota hai (ye class ek @Component hai jo
 * OncePerRequestFilter implement karti hai); config/McpServerConfig ka
 * transport-context extractor yahi request header seedhe padhta hai
 * (MDC ke through nahi, SDK ke internal async dispatch ke against
 * reliable rehne ke liye - us class ka javadoc dekho) aur ise client/
 * ke har downstream client call tak forward karta hai.
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
