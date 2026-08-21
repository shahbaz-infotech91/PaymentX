package com.paymentx.controlcenter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * ENGLISH: Propagates a correlation ID across every request this
 * backend handles, and forwards it to the PaymentX services this
 * backend calls on that request's behalf. What it does: reads
 * X-Correlation-Id from the incoming request (from the React frontend)
 * or generates one if absent, puts it in SLF4J's MDC so every log line
 * for this request includes it, echoes it back on the response header,
 * and clears the MDC when the request completes (thread-pool reuse
 * safety - without clearing, a pooled thread could leak one request's
 * correlation ID into the next). Why it exists: matches the identical
 * CorrelationIdFilter pattern every existing PaymentX service already
 * uses; "correlation ID propagation" is an explicit Phase 2
 * requirement, and ServiceHealthClient reads this same MDC value to
 * forward it as a header when calling the 9 real PaymentX services.
 * How it will communicate with the backend: this IS backend-side
 * request handling - every controller in this module runs behind it.
 *
 * HINGLISH: Is backend ke har request ke across ek correlation ID
 * propagate karta hai, aur us request ki taraf se jo PaymentX services
 * ye backend call karta hai unhe forward karta hai. Ye kya karti hai:
 * incoming request se (React frontend se) X-Correlation-Id padhta hai
 * ya agar na ho toh ek generate karta hai, ise SLF4J ke MDC me daalta
 * hai taaki is request ki har log line me wo shamil ho, response
 * header par wapas echo karta hai, aur request complete hone par MDC
 * clear karta hai (thread-pool reuse safety - clear kiye bina, ek
 * pooled thread ek request ka correlation ID agle me leak kar sakta
 * hai). Ye dashboard me kyu hai: existing har PaymentX service jo
 * identical CorrelationIdFilter pattern already use karti hai usse
 * match karta hai; "correlation ID propagation" ek explicit Phase 2
 * requirement hai, aur ServiceHealthClient 9 real PaymentX services ko
 * call karte waqt isi MDC value ko header ke roop me forward karne ke
 * liye padhta hai. Backend se kaise connect hogi: ye khud backend-side
 * request handling hai - is module ka har controller iske peeche
 * chalta hai.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, correlationId);
            response.setHeader(HEADER_NAME, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
