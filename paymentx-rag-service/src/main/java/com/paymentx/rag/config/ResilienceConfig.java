package com.paymentx.rag.config;

import com.paymentx.rag.exception.RagException;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * English:
 * Supplies the one piece each of this service's FOUR Resilience4j retry
 * instances (embeddingService/vectorService/promptService/llmService)
 * cannot express in YAML alone: WHICH failures are actually retried -
 * matches LLM/Embedding/Vector Service's identical ResilienceConfig
 * pattern (see LlmException's javadoc for the full rationale: every
 * failure this service throws is the same class, RagException, with a
 * `retryable` flag that differs per instance, which YAML's class-based
 * retry-exceptions/ignore-exceptions lists cannot express for a single
 * type). Four separate instances, not one shared instance, because each
 * downstream dependency has its own independent failure/recovery
 * profile and its own timeout tuning (see RagProperties) - a slow
 * Vector Service should not open the circuit breaker guarding calls to
 * LLM Service.
 * Why it exists: Step 30 of the Phase 3.6 brief - "retry only transient
 * failures... do NOT retry invalid request/authorization failure/
 * insufficient context/configuration errors."
 * How it communicates with other components: each bean is discovered by
 * resilience4j-spring-boot3's auto-configuration by matching its
 * registered name to the YAML instance of the same name;
 * @Retry(name = "...") on each client class's call method is where the
 * resulting RetryConfig is actually applied.
 *
 * Hinglish:
 * Is service ke CHAAR Resilience4j retry instances
 * (embeddingService/vectorService/promptService/llmService) me se har
 * ek ka wo ek hissa deta hai jo YAML akele express nahi kar sakti: KAUN
 * se failures actually retry hote hain - LLM/Embedding/Vector Service
 * ke identical ResilienceConfig pattern se match karta hai (poore
 * rationale ke liye LlmException ka javadoc dekho: is service ka har
 * failure same class hai, RagException, ek `retryable` flag ke saath jo
 * per-instance alag hota hai, jise YAML ki class-based retry-exceptions/
 * ignore-exceptions lists ek single type ke liye express nahi kar
 * sakti). Ek shared instance nahi, char alag instances, kyunki har
 * downstream dependency ki apni independent failure/recovery profile
 * aur apna timeout tuning hai (RagProperties dekho) - ek slow Vector
 * Service ko us circuit breaker ko open nahi karna chahiye jo LLM
 * Service ke calls ko guard karta hai.
 * Ye kyu hai: Phase 3.6 brief ka Step 30 - "sirf transient failures
 * retry karo... invalid request/authorization failure/insufficient
 * context/configuration errors par retry MAT karo."
 * Dusre components se kaise communicate karta hai: har bean
 * resilience4j-spring-boot3 ke auto-configuration dwara uske registered
 * naam ko usi naam ke YAML instance se match karke discover hota hai;
 * har client class ke call method par @Retry(name = "...") wahan hai
 * jahan resulting RetryConfig actually apply hota hai.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer embeddingServiceRetryConfigCustomizer() {
        return retryOnRagException("embeddingService");
    }

    @Bean
    public RetryConfigCustomizer vectorServiceRetryConfigCustomizer() {
        return retryOnRagException("vectorService");
    }

    @Bean
    public RetryConfigCustomizer promptServiceRetryConfigCustomizer() {
        return retryOnRagException("promptService");
    }

    @Bean
    public RetryConfigCustomizer llmServiceRetryConfigCustomizer() {
        return retryOnRagException("llmService");
    }

    private RetryConfigCustomizer retryOnRagException(String instanceName) {
        return RetryConfigCustomizer.of(instanceName, builder -> builder.retryOnException(
                throwable -> throwable instanceof RagException ragException && ragException.isRetryable()));
    }
}
