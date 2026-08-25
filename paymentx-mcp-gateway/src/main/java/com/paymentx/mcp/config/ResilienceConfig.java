package com.paymentx.mcp.config;

import com.paymentx.mcp.exception.McpException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * English:
 * Supplies the one piece each of this gateway's FOUR Resilience4j retry
 * instances (paymentService/routingService/reconciliationService/
 * auditService) cannot express in YAML alone: WHICH failures are
 * actually retried - matches RAG/LLM/Embedding Service's identical
 * ResilienceConfig pattern (see McpException's javadoc for the full
 * rationale: every failure this gateway's clients throw is the same
 * class, McpException, with a `retryable` flag that differs per
 * failure kind, which YAML's class-based retry-exceptions/ignore-
 * exceptions lists cannot express for a single type).
 * Why it exists: Step 24 - "retry only safe/transient operations...
 * never blindly retry" - and this module proactively ships with
 * spring-boot-starter-aop from day one (see pom.xml's comment) so these
 * customizer predicates are guaranteed to actually be consulted at call
 * time, unlike the real gap Phase 3.6 found and fixed in three other
 * AI Platform services.
 * How it communicates with other components: each bean is discovered by
 * resilience4j-spring-boot3's auto-configuration by matching its
 * registered name to the YAML instance of the same name;
 * @Retry(name = "...") on each client class's call method is where the
 * resulting RetryConfig is actually applied.
 *
 * Hinglish:
 * Is gateway ke CHAAR Resilience4j retry instances
 * (paymentService/routingService/reconciliationService/auditService) me
 * se har ek ka wo ek hissa deta hai jo YAML akele express nahi kar
 * sakti: KAUN se failures actually retry hote hain - RAG/LLM/Embedding
 * Service ke identical ResilienceConfig pattern se match karta hai
 * (poore rationale ke liye McpException ka javadoc dekho: is gateway ke
 * clients ka har failure same class hai, McpException, ek `retryable`
 * flag ke saath jo per-failure-kind alag hota hai, jise YAML ki class-
 * based retry-exceptions/ignore-exceptions lists ek single type ke liye
 * express nahi kar sakti).
 * Ye kyu hai: Step 24 - "sirf safe/transient operations retry karo...
 * kabhi blindly retry mat karo" - aur ye module pehle din se hi
 * proactively spring-boot-starter-aop ke saath ship hota hai (pom.xml ka
 * comment dekho) taaki in customizer predicates ko call time par
 * actually consult kiya jaana guaranteed ho, us real gap ke ulat jo
 * Phase 3.6 ne teen doosri AI Platform services me dhoonda aur fix kiya.
 * Dusre components se kaise communicate karta hai: har bean
 * resilience4j-spring-boot3 ke auto-configuration dwara uske registered
 * naam ko usi naam ke YAML instance se match karke discover hota hai;
 * har client class ke call method par @Retry(name = "...") wahan hai
 * jahan resulting RetryConfig actually apply hota hai.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer paymentServiceRetryConfigCustomizer() {
        return retryOnMcpException("paymentService");
    }

    @Bean
    public RetryConfigCustomizer routingServiceRetryConfigCustomizer() {
        return retryOnMcpException("routingService");
    }

    @Bean
    public RetryConfigCustomizer reconciliationServiceRetryConfigCustomizer() {
        return retryOnMcpException("reconciliationService");
    }

    @Bean
    public RetryConfigCustomizer auditServiceRetryConfigCustomizer() {
        return retryOnMcpException("auditService");
    }

    /** Phase 4.4 - client/ControlCenterClient's retry instance, same retryable-predicate pattern. */
    @Bean
    public RetryConfigCustomizer controlCenterRetryConfigCustomizer() {
        return retryOnMcpException("controlCenter");
    }

    private RetryConfigCustomizer retryOnMcpException(String instanceName) {
        return RetryConfigCustomizer.of(instanceName, builder -> builder.retryOnException(
                throwable -> throwable instanceof McpException mcpException && mcpException.isRetryable()));
    }
}
