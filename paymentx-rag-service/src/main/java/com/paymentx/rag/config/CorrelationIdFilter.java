package com.paymentx.rag.config;

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
 * response header. RagServiceImpl reads this same MDC value and
 * forwards it as X-Correlation-Id on every one of its four downstream
 * calls (EmbeddingServiceClient/VectorServiceClient/PromptServiceClient/
 * LlmServiceClient) - this is the one real mechanism behind Step 27's
 * distributed-trace requirement: Control Center -> AI Chat -> RAG
 * Service -> Embedding/Vector/Prompt/LLM Service -> LLM Provider all
 * carry the same correlation ID end to end, without introducing a
 * second tracing system.
 * Why it exists: Step 26/27's structured-logging and distributed-trace
 * requirements.
 * How it communicates with other components: every controller in this
 * module runs behind it; RagServiceImpl reads CorrelationIdFilter.MDC_KEY
 * to propagate the same ID to every downstream client call.
 *
 * Hinglish:
 * Har doosri AI Platform service ke CorrelationIdFilter se exactly
 * match karta hai (har servlet-based PaymentX service apni khud ki copy
 * rakhti hai - paymentx-common-library se shared nahi). Incoming
 * request se X-Correlation-Id padhta hai ya agar na ho toh ek generate
 * karta hai, ise MDC me daalta hai taaki is request ki har log line me
 * wo shamil ho, response header par wapas echo karta hai. RagServiceImpl
 * yahi MDC value padhta hai aur ise apne char downstream calls
 * (EmbeddingServiceClient/VectorServiceClient/PromptServiceClient/
 * LlmServiceClient) me se har ek par X-Correlation-Id ke roop me
 * forward karta hai - Step 27 ki distributed-trace requirement ke
 * peeche yahi ek real mechanism hai: Control Center -> AI Chat -> RAG
 * Service -> Embedding/Vector/Prompt/LLM Service -> LLM Provider sab
 * end to end same correlation ID carry karte hain, bina ek doosri
 * tracing system introduce kiye.
 * Ye kyu hai: Step 26/27 ki structured-logging aur distributed-trace
 * requirements.
 * Dusre components se kaise communicate karta hai: is module ka har
 * controller iske peeche chalta hai; RagServiceImpl
 * CorrelationIdFilter.MDC_KEY padhta hai isi ID ko har downstream client
 * call tak propagate karne ke liye.
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
